package com.example.demo.web.dto;

import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @Positive long dishId,
        @Positive int qty) {
}
