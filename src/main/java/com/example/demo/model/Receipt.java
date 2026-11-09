package com.example.demo.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record Receipt(
        String receiptNo,
        Long orderId,
        String channel,
        OffsetDateTime closedAt,
        Long customerId,
        String customerName,
        String customerPhone,
        BigDecimal customerDiscountPct,
        Long waiterId,
        String waiterName,
        Integer tableId,
        Integer tableNumber,
        String hall,
        Integer guests,
        String deliveryAddress,
        String courierName,
        Integer etaMinutes,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        String paymentMethod,
        OffsetDateTime paidAt,
        Integer rating,
        String feedbackComment,
        List<ReceiptItem> items,
        List<String> tags
) {
}
