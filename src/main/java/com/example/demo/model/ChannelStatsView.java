package com.example.demo.model;

import java.math.BigDecimal;

public record ChannelStatsView(
        String channel,
        long receipts,
        BigDecimal revenue,
        BigDecimal avgReceipt,
        BigDecimal avgPositions
) {
}
