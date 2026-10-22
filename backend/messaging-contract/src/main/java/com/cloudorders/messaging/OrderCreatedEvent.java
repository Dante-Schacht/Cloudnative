package com.cloudorders.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento publicado cuando un pedido es aceptado. Refleja los campos reales del pedido
 * (ver Order en order-service); es el único contrato de mensajería compartido entre servicios.
 */
public record OrderCreatedEvent(
        @NotNull(message = "eventId es obligatorio")
        UUID eventId,

        @NotBlank(message = "eventType es obligatorio")
        @Pattern(regexp = OrderCreatedEvent.EVENT_TYPE, message = "eventType debe ser " + OrderCreatedEvent.EVENT_TYPE)
        String eventType,

        @NotNull(message = "orderId es obligatorio")
        Long orderId,

        @NotBlank(message = "customerName es obligatorio")
        String customerName,

        @NotBlank(message = "product es obligatorio")
        String product,

        @Positive(message = "quantity debe ser mayor que cero")
        int quantity,

        String notes,

        @NotBlank(message = "status es obligatorio")
        String status,

        @NotNull(message = "createdAt es obligatorio")
        OffsetDateTime createdAt) {

    public static final String EVENT_TYPE = "ORDER_CREATED";
}
