package com.example.demo.model;

import java.math.BigDecimal;

public record ReceiptModifier(
        String name,
        BigDecimal priceDelta
) {
}
