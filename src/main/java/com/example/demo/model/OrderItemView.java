package com.example.demo.model;

import java.math.BigDecimal;

public record OrderItemView(
        long itemId,
        long orderId,
        long dishId,
        String dish,
        String category,
        int qty,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
