package com.cloudorders.admin.dto;

public record QueueStatsResponse(String name, int messageCount, int consumerCount) {
}
