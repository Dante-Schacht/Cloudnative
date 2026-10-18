package com.cloudorders.orders.controller;

import com.cloudorders.orders.dto.HealthResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String serviceName;
    private final Clock clock;

    public HealthController(@Value("${spring.application.name}") String serviceName, Clock clock) {
        this.serviceName = serviceName;
        this.clock = clock;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", serviceName, OffsetDateTime.now(clock));
    }
}
