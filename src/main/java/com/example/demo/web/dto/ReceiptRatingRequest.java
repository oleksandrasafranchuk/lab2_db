package com.example.demo.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReceiptRatingRequest(
        @Min(1) @Max(5) int rating,
        String comment
) {
}
