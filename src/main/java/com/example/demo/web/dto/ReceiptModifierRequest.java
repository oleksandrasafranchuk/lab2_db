package com.example.demo.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ReceiptModifierRequest(
        @Positive int lineNo,
        @NotBlank String name,
        BigDecimal priceDelta
) {
}
