package com.example.demo.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReceiptRef(
        String receiptNo,
        OffsetDateTime closedAt,
        BigDecimal total,
        String dish
) {
}
