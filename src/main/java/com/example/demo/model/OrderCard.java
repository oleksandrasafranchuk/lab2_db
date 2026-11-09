package com.example.demo.model;

import java.util.List;

public record OrderCard(
        OrderView order,
        List<OrderItemView> items,
        List<PaymentView> payments
) {
}
