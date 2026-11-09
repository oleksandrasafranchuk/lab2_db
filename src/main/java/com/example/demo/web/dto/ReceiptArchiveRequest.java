package com.example.demo.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ReceiptArchiveRequest(
        @Pattern(regexp = "DINE_IN|DELIVERY|TAKEAWAY") String channel,
        @Valid List<ReceiptModifierRequest> modifiers,
        List<String> tags,
        String deliveryAddress,
        String courierName,
        Integer etaMinutes,
        Integer guests
) {

    public String channelOrDefault() {
        return channel == null || channel.isBlank() ? "DINE_IN" : channel;
    }

    public List<ReceiptModifierRequest> modifiersOrEmpty() {
        return modifiers == null ? List.of() : modifiers;
    }

    public List<String> tagsOrEmpty() {
        return tags == null ? List.of() : tags;
    }
}
