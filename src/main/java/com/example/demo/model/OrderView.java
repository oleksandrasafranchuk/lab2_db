package com.example.demo.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderView(
        long id,
        String status,
        BigDecimal total,
        OffsetDateTime createdAt,
        OffsetDateTime closedAt,
        int tableId,
        int tableNumber,
        String hall,
        long waiterId,
        String waiter,
        Long customerId,
        String customer,
        String customerPhone,
        BigDecimal customerDiscountPct,
        int itemsCount
) {
}
