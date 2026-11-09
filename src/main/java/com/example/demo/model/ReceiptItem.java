package com.example.demo.model;

import java.math.BigDecimal;
import java.util.List;

public record ReceiptItem(
        int lineNo,
        long dishId,
        String dish,
        String category,
        int qty,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        List<ReceiptModifier> modifiers
) {
}
