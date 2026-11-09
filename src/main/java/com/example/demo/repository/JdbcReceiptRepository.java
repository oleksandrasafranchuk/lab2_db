package com.example.demo.repository;

import com.example.demo.model.ChannelStatsView;
import com.example.demo.model.Receipt;
import com.example.demo.model.ReceiptItem;
import com.example.demo.model.ReceiptModifier;
import com.example.demo.model.ReceiptRef;
import com.example.demo.model.TopDishView;
import com.example.demo.uow.SqlExecutor;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JdbcReceiptRepository implements ReceiptRepository {

    private static final String SELECT_LINES = """
            SELECT receipt_id, receipt_no, order_id, channel, closed_at,
                   customer_id, customer_name, customer_phone, customer_discount_pct,
                   waiter_id, waiter_name, table_id, table_number, hall, guests,
                   delivery_address, courier_name, eta_minutes,
                   subtotal, discount, total, payment_method, paid_at,
                   rating, feedback_comment, tags,
                   item_id, line_no, dish_id, dish_name, category, qty, unit_price, line_total,
                   modifier_id, modifier_name, price_delta
            FROM v_receipt_lines
            """;

    private static final String CALL_INSERT = """
            CALL sp_receipt_insert(
                CAST(? AS TEXT), CAST(? AS BIGINT), CAST(? AS TEXT), CAST(? AS TIMESTAMPTZ),
                CAST(? AS BIGINT), CAST(? AS TEXT), CAST(? AS TEXT), CAST(? AS NUMERIC),
                CAST(? AS BIGINT), CAST(? AS TEXT),
                CAST(? AS INT), CAST(? AS INT), CAST(? AS TEXT), CAST(? AS INT),
                CAST(? AS TEXT), CAST(? AS TEXT), CAST(? AS INT),
                CAST(? AS NUMERIC), CAST(? AS NUMERIC), CAST(? AS NUMERIC),
                CAST(? AS TEXT), CAST(? AS TIMESTAMPTZ),
                CAST(? AS INT), CAST(? AS TEXT),
                CAST(? AS BIGINT[]), CAST(? AS TEXT[]), CAST(? AS TEXT[]),
                CAST(? AS INT[]), CAST(? AS NUMERIC[]), CAST(? AS NUMERIC[]),
                CAST(? AS INT[]), CAST(? AS TEXT[]), CAST(? AS NUMERIC[]),
                CAST(? AS TEXT[]),
                NULL)
            """;

    private final SqlExecutor sql;

    public JdbcReceiptRepository(SqlExecutor sql) {
        this.sql = sql;
    }

    @Override
    public long insert(Receipt receipt) {
        List<ReceiptItem> items = receipt.items();
        int size = items.size();

        Long[] dishIds = new Long[size];
        String[] names = new String[size];
        String[] categories = new String[size];
        Integer[] quantities = new Integer[size];
        BigDecimal[] prices = new BigDecimal[size];
        BigDecimal[] lineTotals = new BigDecimal[size];

        List<Integer> modifierLines = new ArrayList<>();
        List<String> modifierNames = new ArrayList<>();
        List<BigDecimal> modifierDeltas = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            ReceiptItem item = items.get(i);
            dishIds[i] = item.dishId();
            names[i] = item.dish();
            categories[i] = item.category();
            quantities[i] = item.qty();
            prices[i] = item.unitPrice();
            lineTotals[i] = item.lineTotal();
            for (ReceiptModifier modifier : item.modifiers()) {
                modifierLines.add(item.lineNo());
                modifierNames.add(modifier.name());
                modifierDeltas.add(modifier.priceDelta());
            }
        }

        Array itemDishIds = sql.array("bigint", dishIds);
        Array itemNames = sql.array("text", names);
        Array itemCategories = sql.array("text", categories);
        Array itemQuantities = sql.array("int4", quantities);
        Array itemPrices = sql.array("numeric", prices);
        Array itemLineTotals = sql.array("numeric", lineTotals);
        Array modLines = sql.array("int4", modifierLines.toArray(Integer[]::new));
        Array modNames = sql.array("text", modifierNames.toArray(String[]::new));
        Array modDeltas = sql.array("numeric", modifierDeltas.toArray(BigDecimal[]::new));
        Array tags = sql.array("text", receipt.tags().toArray(String[]::new));

        return sql.callForId(CALL_INSERT,
                receipt.receiptNo(), receipt.orderId(), receipt.channel(), receipt.closedAt(),
                receipt.customerId(), receipt.customerName(), receipt.customerPhone(), receipt.customerDiscountPct(),
                receipt.waiterId(), receipt.waiterName(),
                receipt.tableId(), receipt.tableNumber(), receipt.hall(), receipt.guests(),
                receipt.deliveryAddress(), receipt.courierName(), receipt.etaMinutes(),
                receipt.subtotal(), receipt.discount(), receipt.total(),
                receipt.paymentMethod(), receipt.paidAt(),
                receipt.rating(), receipt.feedbackComment(),
                itemDishIds, itemNames, itemCategories, itemQuantities, itemPrices, itemLineTotals,
                modLines, modNames, modDeltas,
                tags);
    }

    @Override
    public Optional<Receipt> findByNo(String receiptNo) {
        List<Line> lines = sql.query(
                SELECT_LINES + "WHERE receipt_no = CAST(? AS TEXT) ORDER BY line_no, modifier_id",
                JdbcReceiptRepository::line,
                receiptNo);
        List<Receipt> receipts = assemble(lines);
        return receipts.isEmpty() ? Optional.empty() : Optional.of(receipts.getFirst());
    }

    @Override
    public List<Receipt> findByCustomerAndPeriod(long customerId, OffsetDateTime from, OffsetDateTime to) {
        List<Line> lines = sql.query(
                SELECT_LINES + """
                        WHERE customer_id = CAST(? AS BIGINT)
                          AND closed_at >= CAST(? AS TIMESTAMPTZ)
                          AND closed_at < CAST(? AS TIMESTAMPTZ)
                        ORDER BY closed_at DESC, receipt_id, line_no, modifier_id
                        """,
                JdbcReceiptRepository::line,
                customerId, from, to);
        return assemble(lines);
    }

    @Override
    public List<TopDishView> topDishes(OffsetDateTime from, OffsetDateTime to, int limit) {
        return sql.query(
                """
                        SELECT dish_id, dish, sold_qty, revenue
                        FROM fn_receipt_top_dishes(CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ), CAST(? AS INT))
                        """,
                RowMappers::topDish,
                from, to, limit);
    }

    @Override
    public List<ReceiptRef> findByModifier(String modifier, int limit) {
        return sql.query(
                """
                        SELECT receipt_no, closed_at, total, dish
                        FROM fn_receipts_by_modifier(CAST(? AS TEXT), CAST(? AS INT))
                        """,
                RowMappers::receiptRef,
                modifier, limit);
    }

    @Override
    public List<ChannelStatsView> channelStats(OffsetDateTime from, OffsetDateTime to) {
        return sql.query(
                """
                        SELECT channel, receipts, revenue, avg_receipt, avg_positions
                        FROM fn_receipt_channel_stats(CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))
                        """,
                RowMappers::channelStats,
                from, to);
    }

    @Override
    public void rate(String receiptNo, int rating, String comment) {
        sql.call(
                "CALL sp_receipt_rate(CAST(? AS TEXT), CAST(? AS INT), CAST(? AS TEXT))",
                receiptNo, rating, comment);
    }

    @Override
    public long count() {
        return sql.queryValue("SELECT count(*) AS total FROM v_receipts", rs -> rs.getLong("total"));
    }

    @Override
    public void clear() {
        sql.call("CALL sp_receipts_clear()");
    }

    @Override
    public void analyze() {
        sql.call("CALL sp_receipts_analyze()");
    }

    private List<Receipt> assemble(List<Line> lines) {
        Map<Long, Draft> drafts = new LinkedHashMap<>();
        for (Line line : lines) {
            Draft draft = drafts.computeIfAbsent(line.receiptId(), id -> new Draft(line));
            if (line.itemId() == null) {
                continue;
            }
            ItemDraft item = draft.items.computeIfAbsent(line.itemId(), id -> new ItemDraft(line));
            if (line.modifierName() != null) {
                item.modifiers.add(new ReceiptModifier(line.modifierName(), line.priceDelta()));
            }
        }

        List<Receipt> receipts = new ArrayList<>(drafts.size());
        for (Draft draft : drafts.values()) {
            List<ReceiptItem> items = new ArrayList<>(draft.items.size());
            for (ItemDraft item : draft.items.values()) {
                Line source = item.source;
                items.add(new ReceiptItem(
                        source.lineNo(),
                        source.dishId(),
                        source.dishName(),
                        source.category(),
                        source.qty(),
                        source.unitPrice(),
                        source.lineTotal(),
                        List.copyOf(item.modifiers)));
            }
            Line head = draft.head;
            receipts.add(new Receipt(
                    head.receiptNo(), head.orderId(), head.channel(), head.closedAt(),
                    head.customerId(), head.customerName(), head.customerPhone(), head.customerDiscountPct(),
                    head.waiterId(), head.waiterName(),
                    head.tableId(), head.tableNumber(), head.hall(), head.guests(),
                    head.deliveryAddress(), head.courierName(), head.etaMinutes(),
                    head.subtotal(), head.discount(), head.total(),
                    head.paymentMethod(), head.paidAt(),
                    head.rating(), head.feedbackComment(),
                    items,
                    head.tags()));
        }
        return receipts;
    }

    private static final class Draft {

        private final Line head;
        private final Map<Long, ItemDraft> items = new LinkedHashMap<>();

        private Draft(Line head) {
            this.head = head;
        }
    }

    private static final class ItemDraft {

        private final Line source;
        private final List<ReceiptModifier> modifiers = new ArrayList<>();

        private ItemDraft(Line source) {
            this.source = source;
        }
    }

    private record Line(
            long receiptId,
            String receiptNo,
            Long orderId,
            String channel,
            OffsetDateTime closedAt,
            Long customerId,
            String customerName,
            String customerPhone,
            BigDecimal customerDiscountPct,
            Long waiterId,
            String waiterName,
            Integer tableId,
            Integer tableNumber,
            String hall,
            Integer guests,
            String deliveryAddress,
            String courierName,
            Integer etaMinutes,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total,
            String paymentMethod,
            OffsetDateTime paidAt,
            Integer rating,
            String feedbackComment,
            List<String> tags,
            Long itemId,
            int lineNo,
            long dishId,
            String dishName,
            String category,
            int qty,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String modifierName,
            BigDecimal priceDelta
    ) {
    }

    private static Line line(ResultSet rs) throws SQLException {
        return new Line(
                rs.getLong("receipt_id"),
                rs.getString("receipt_no"),
                rs.getObject("order_id", Long.class),
                rs.getString("channel"),
                rs.getObject("closed_at", OffsetDateTime.class),
                rs.getObject("customer_id", Long.class),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                rs.getBigDecimal("customer_discount_pct"),
                rs.getObject("waiter_id", Long.class),
                rs.getString("waiter_name"),
                rs.getObject("table_id", Integer.class),
                rs.getObject("table_number", Integer.class),
                rs.getString("hall"),
                rs.getObject("guests", Integer.class),
                rs.getString("delivery_address"),
                rs.getString("courier_name"),
                rs.getObject("eta_minutes", Integer.class),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("discount"),
                rs.getBigDecimal("total"),
                rs.getString("payment_method"),
                rs.getObject("paid_at", OffsetDateTime.class),
                rs.getObject("rating", Integer.class),
                rs.getString("feedback_comment"),
                tags(rs.getArray("tags")),
                rs.getObject("item_id", Long.class),
                rs.getInt("line_no"),
                rs.getLong("dish_id"),
                rs.getString("dish_name"),
                rs.getString("category"),
                rs.getInt("qty"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("line_total"),
                rs.getString("modifier_name"),
                rs.getBigDecimal("price_delta"));
    }

    private static List<String> tags(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }
}
