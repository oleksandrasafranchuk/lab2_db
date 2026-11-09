package com.example.demo.service;

import com.example.demo.model.ChannelStatsView;
import com.example.demo.model.OrderItemView;
import com.example.demo.model.OrderView;
import com.example.demo.model.PaymentView;
import com.example.demo.model.Receipt;
import com.example.demo.model.ReceiptItem;
import com.example.demo.model.ReceiptModifier;
import com.example.demo.model.ReceiptRef;
import com.example.demo.model.TopDishView;
import com.example.demo.nosql.mongo.MongoReceiptRepository;
import com.example.demo.nosql.redis.RedisReceiptBoard;
import com.example.demo.repository.OrderRepository;
import com.example.demo.uow.BusinessRuleException;
import com.example.demo.uow.ResourceNotFoundException;
import com.example.demo.uow.UnitOfWorkFactory;
import com.example.demo.web.dto.ReceiptArchiveRequest;
import com.example.demo.web.dto.ReceiptModifierRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    private final UnitOfWorkFactory factory;
    private final MongoReceiptRepository receipts;
    private final RedisReceiptBoard board;

    public ReceiptService(UnitOfWorkFactory factory, MongoReceiptRepository receipts, RedisReceiptBoard board) {
        this.factory = factory;
        this.receipts = receipts;
        this.board = board;
    }

    public Receipt archive(long orderId, ReceiptArchiveRequest request) {
        Receipt receipt = factory.execute(unitOfWork -> {
            OrderRepository orders = unitOfWork.orders();
            OrderView order = orders.findById(orderId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
            if (!"PAID".equals(order.status())) {
                throw new BusinessRuleException("Order " + orderId + " is not paid yet");
            }

            List<OrderItemView> orderItems = orders.findItems(orderId);
            if (orderItems.isEmpty()) {
                throw new BusinessRuleException("Order " + orderId + " has no items");
            }
            PaymentView payment = orders.findPayments(orderId).stream()
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> ResourceNotFoundException.of("Payment for order", orderId));

            return build(order, orderItems, payment, request);
        });

        if (receipts.findByNo(receipt.receiptNo()).isPresent()) {
            throw new BusinessRuleException("Receipt for order " + orderId + " already archived");
        }
        receipts.insert(receipt);
        register(receipt);
        return receipt;
    }

    public Receipt byNo(String receiptNo) {
        Optional<Receipt> cached = cached(receiptNo);
        if (cached.isPresent()) {
            return cached.get();
        }
        Receipt receipt = receipts.findByNo(receiptNo)
                .orElseThrow(() -> ResourceNotFoundException.of("Receipt", receiptNo));
        try {
            board.cache(receipt);
        } catch (RuntimeException e) {
            log.warn("Receipt {} was not cached: {}", receiptNo, e.getMessage());
        }
        return receipt;
    }

    public List<Receipt> customerHistory(long customerId, OffsetDateTime from, OffsetDateTime to) {
        return receipts.findByCustomerAndPeriod(customerId, from, to);
    }

    public List<TopDishView> topDishes(OffsetDateTime from, OffsetDateTime to, int limit) {
        return receipts.topDishes(from, to, limit);
    }

    public List<ReceiptRef> byModifier(String modifier, int limit) {
        return receipts.findByModifier(modifier, limit);
    }

    public List<ChannelStatsView> channelStats(OffsetDateTime from, OffsetDateTime to) {
        return receipts.channelStats(from, to);
    }

    public void rate(String receiptNo, int rating, String comment) {
        if (receipts.findByNo(receiptNo).isEmpty()) {
            throw ResourceNotFoundException.of("Receipt", receiptNo);
        }
        receipts.rate(receiptNo, rating, comment);
        board.evict(receiptNo);
    }

    private void register(Receipt receipt) {
        try {
            board.register(receipt);
        } catch (RuntimeException e) {
            log.warn("Receipt {} saved, but live board was not updated: {}", receipt.receiptNo(), e.getMessage());
        }
    }

    private Optional<Receipt> cached(String receiptNo) {
        try {
            return board.cached(receiptNo);
        } catch (RuntimeException e) {
            log.warn("Cache lookup for {} failed, falling back to MongoDB: {}", receiptNo, e.getMessage());
            return Optional.empty();
        }
    }

    private static Receipt build(OrderView order,
                                 List<OrderItemView> orderItems,
                                 PaymentView payment,
                                 ReceiptArchiveRequest request) {
        String channel = request.channelOrDefault();
        boolean delivery = "DELIVERY".equals(channel);

        List<ReceiptItem> items = new ArrayList<>(orderItems.size());
        BigDecimal subtotal = BigDecimal.ZERO;
        int lineNo = 0;

        for (OrderItemView orderItem : orderItems) {
            lineNo++;
            List<ReceiptModifier> modifiers = modifiersFor(lineNo, request.modifiersOrEmpty());

            BigDecimal deltas = BigDecimal.ZERO;
            for (ReceiptModifier modifier : modifiers) {
                deltas = deltas.add(modifier.priceDelta());
            }
            BigDecimal lineTotal = orderItem.unitPrice()
                    .add(deltas)
                    .multiply(BigDecimal.valueOf(orderItem.qty()))
                    .setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);

            items.add(new ReceiptItem(lineNo, orderItem.dishId(), orderItem.dish(), orderItem.category(),
                    orderItem.qty(), orderItem.unitPrice(), lineTotal, modifiers));
        }

        BigDecimal discountPct = order.customerDiscountPct() == null
                ? BigDecimal.ZERO
                : order.customerDiscountPct();
        BigDecimal discount = subtotal
                .multiply(discountPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new Receipt(
                "R-%07d".formatted(order.id()),
                order.id(),
                channel,
                order.closedAt() == null ? payment.paidAt() : order.closedAt(),
                order.customerId(),
                order.customer(),
                order.customerPhone(),
                discountPct,
                order.waiterId(),
                order.waiter(),
                delivery ? null : order.tableId(),
                delivery ? null : order.tableNumber(),
                delivery ? null : order.hall(),
                delivery ? null : request.guests(),
                delivery ? request.deliveryAddress() : null,
                delivery ? request.courierName() : null,
                delivery ? request.etaMinutes() : null,
                subtotal.setScale(2, RoundingMode.HALF_UP),
                discount,
                subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP),
                payment.method(),
                payment.paidAt(),
                null,
                null,
                items,
                List.copyOf(request.tagsOrEmpty()));
    }

    private static List<ReceiptModifier> modifiersFor(int lineNo, List<ReceiptModifierRequest> requested) {
        List<ReceiptModifier> modifiers = new ArrayList<>();
        for (ReceiptModifierRequest modifier : requested) {
            if (modifier.lineNo() == lineNo) {
                modifiers.add(new ReceiptModifier(
                        modifier.name(),
                        modifier.priceDelta() == null ? BigDecimal.ZERO : modifier.priceDelta()));
            }
        }
        return List.copyOf(modifiers);
    }
}
