package com.cloudorders.orders.dto;

import com.cloudorders.orders.model.Order;
import com.cloudorders.orders.model.OrderStatus;
import java.time.OffsetDateTime;

public record OrderResponse(
        Long id,
        String customerName,
        String product,
        int quantity,
        String notes,
        OrderStatus status,
        OffsetDateTime createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.customerName(),
                order.product(),
                order.quantity(),
                order.notes(),
                order.status(),
                order.createdAt());
    }
}
