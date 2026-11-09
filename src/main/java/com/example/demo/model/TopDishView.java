package com.example.demo.model;

import java.math.BigDecimal;

public record TopDishView(
        long dishId,
        String dish,
        long soldQty,
        BigDecimal revenue
) {
}
