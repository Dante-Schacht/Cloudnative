package com.cloudorders.orders.model;

import java.time.OffsetDateTime;

public record Order(
        Long id,
        String customerName,
        String product,
        int quantity,
        String notes,
        OrderStatus status,
        OffsetDateTime createdAt) {
}
