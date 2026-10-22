package com.cloudorders.admin.dto;

public record QueueResponse(String name, boolean durable, QueueType type) {
}
