package com.delivery.routing_service.api;

public record EtaWindowResponse(
        int minMinutes,
        int maxMinutes,
        String source) {
}
