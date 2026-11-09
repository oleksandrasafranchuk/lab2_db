package com.example.demo.repository;

import com.example.demo.model.OrderItemView;
import com.example.demo.model.OrderView;
import com.example.demo.model.PaymentView;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Optional<OrderView> findById(long id);

    List<OrderItemView> findItems(long orderId);

    List<PaymentView> findPayments(long orderId);

    long create(int tableId, long waiterId, Long customerId);

    long addItem(long orderId, long dishId, int qty);

    long pay(long orderId, String method);
}
