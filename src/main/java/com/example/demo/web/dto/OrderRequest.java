package com.example.demo.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record OrderRequest(
        @Positive int tableId,
        @Positive long waiterId,
        Long customerId,
        @NotEmpty @Valid List<OrderItemRequest> items,
        @Pattern(regexp = "CASH|CARD") String paymentMethod) {

    public String paymentMethodOrDefault() {
        return paymentMethod == null || paymentMethod.isBlank() ? "CARD" : paymentMethod;
    }
}
