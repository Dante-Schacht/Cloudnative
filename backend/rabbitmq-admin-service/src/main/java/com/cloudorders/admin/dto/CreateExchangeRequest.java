package com.cloudorders.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateExchangeRequest(
        @NotBlank(message = "El nombre del exchange es obligatorio.")
        @Pattern(regexp = ResourceNames.NAME_PATTERN,
                message = "El nombre solo admite letras, números, '.', '_' y '-' (máx. 200 caracteres).")
        String name,

        @NotNull(message = "El tipo de exchange es obligatorio (DIRECT, TOPIC, FANOUT o HEADERS).")
        ExchangeKind type,

        Boolean durable) {

    public boolean durableOrDefault() {
        return durable == null || durable;
    }
}
