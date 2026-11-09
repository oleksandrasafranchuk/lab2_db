package com.example.demo.web;

import java.util.List;

public record ApiError(
        int status,
        String error,
        String message,
        List<String> details
) {
}
