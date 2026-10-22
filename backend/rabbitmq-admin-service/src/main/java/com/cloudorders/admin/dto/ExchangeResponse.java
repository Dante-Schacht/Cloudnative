package com.cloudorders.admin.dto;

public record ExchangeResponse(String name, ExchangeKind type, boolean durable) {
}
