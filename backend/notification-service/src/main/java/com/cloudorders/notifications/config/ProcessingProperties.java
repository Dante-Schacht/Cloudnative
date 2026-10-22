package com.cloudorders.notifications.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cloudorders.processing")
public record ProcessingProperties(
        @Min(1) int maxAttempts,
        @NotNull Duration backoff,
        @Min(1) int idempotencyCacheSize,
        Simulation simulation) {

    public record Simulation(boolean enabled) {
    }

    public boolean simulationEnabled() {
        return simulation != null && simulation.enabled();
    }
}
