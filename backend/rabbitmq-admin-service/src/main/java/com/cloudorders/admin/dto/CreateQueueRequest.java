package com.cloudorders.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateQueueRequest(
        @NotBlank(message = "El nombre de la cola es obligatorio.")
        @Pattern(regexp = ResourceNames.NAME_PATTERN,
                message = "El nombre solo admite letras, números, '.', '_' y '-' (máx. 200 caracteres).")
        String name,

        Boolean durable,

        QueueType type) {

    public boolean durableOrDefault() {
        return durable == null || durable;
    }

    public QueueType typeOrDefault() {
        return type == null ? QueueType.QUORUM : type;
    }

    @JsonIgnore
    @AssertTrue(message = "Una cola quorum debe ser durable.")
    public boolean isQuorumDurable() {
        return typeOrDefault() != QueueType.QUORUM || durableOrDefault();
    }
}
