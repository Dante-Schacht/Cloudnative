package com.cloudorders.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Identifica un binding de forma unívoca: exchange + cola + routing key. */
public record BindingRequest(
        @NotBlank(message = "El exchange es obligatorio.")
        @Pattern(regexp = ResourceNames.NAME_PATTERN, message = "Nombre de exchange inválido.")
        String exchange,

        @NotBlank(message = "La cola es obligatoria.")
        @Pattern(regexp = ResourceNames.NAME_PATTERN, message = "Nombre de cola inválido.")
        String queue,

        @NotNull(message = "La routing key es obligatoria (puede ser vacía).")
        @Pattern(regexp = ResourceNames.ROUTING_KEY_PATTERN,
                message = "La routing key solo admite letras, números, '.', '_', '-', '*' y '#'.")
        String routingKey) {
}
