package com.example.demo.service;

import com.example.demo.model.OrderCard;
import com.example.demo.model.OrderView;
import com.example.demo.repository.OrderRepository;
import com.example.demo.uow.ResourceNotFoundException;
import com.example.demo.uow.UnitOfWorkFactory;
import com.example.demo.web.dto.OrderItemRequest;
import com.example.demo.web.dto.OrderRequest;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final UnitOfWorkFactory factory;

    public OrderService(UnitOfWorkFactory factory) {
        this.factory = factory;
    }

    public OrderCard serve(OrderRequest request) {
        return factory.execute(unitOfWork -> {
            OrderRepository orders = unitOfWork.orders();

            long orderId = orders.create(request.tableId(), request.waiterId(), request.customerId());
            for (OrderItemRequest item : request.items()) {
                orders.addItem(orderId, item.dishId(), item.qty());
            }
            orders.pay(orderId, request.paymentMethodOrDefault());

            OrderView order = orders.findById(orderId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
            return new OrderCard(order, orders.findItems(orderId), orders.findPayments(orderId));
        });
    }
}
