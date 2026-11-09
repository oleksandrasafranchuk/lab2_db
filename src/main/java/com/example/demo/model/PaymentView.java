package com.example.demo.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentView(
        long id,
        long orderId,
        String method,
        BigDecimal amount,
        OffsetDateTime paidAt
) {
}
