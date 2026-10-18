package com.cloudorders.orders.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotBlank(message = "El nombre del cliente es obligatorio.")
        @Size(max = 120, message = "El nombre del cliente no puede superar los 120 caracteres.")
        String customerName,

        @NotBlank(message = "El producto es obligatorio.")
        @Size(max = 120, message = "El producto no puede superar los 120 caracteres.")
        String product,

        @NotNull(message = "La cantidad es obligatoria.")
        @Min(value = 1, message = "La cantidad debe ser un número entero mayor que cero.")
        Integer quantity,

        @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres.")
        String notes) {
}
