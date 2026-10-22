package com.cloudorders.orders.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Nombres de la topología RabbitMQ, definidos exclusivamente en application.yml. */
@Validated
@ConfigurationProperties("cloudorders.rabbitmq")
public record RabbitTopologyProperties(
        @NotBlank String exchange,
        @NotBlank String deadLetterExchange,
        @NotBlank String orderCreatedRoutingKey,
        @NotNull Duration publishTimeout,
        @NotNull @Valid Queues queues) {

    /** Colas de la topología; cada una con su binding y su DLQ. */
    public record Queues(
            @NotNull @Valid QueueSpec created,
            @NotNull @Valid QueueSpec audit,
            @NotNull @Valid QueueSpec inventory) {
    }

    public record QueueSpec(
            @NotBlank String name,
            @NotBlank String bindingKey,
            @NotBlank String deadLetterRoutingKey,
            @NotBlank String deadLetterQueue) {
    }
}
