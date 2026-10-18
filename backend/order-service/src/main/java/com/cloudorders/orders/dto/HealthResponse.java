package com.cloudorders.orders.dto;

import java.time.OffsetDateTime;

public record HealthResponse(String status, String service, OffsetDateTime timestamp) {
}
