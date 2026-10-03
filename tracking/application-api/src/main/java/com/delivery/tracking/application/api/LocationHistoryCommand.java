package com.delivery.tracking.application.api;
import java.util.UUID;
public record LocationHistoryCommand(Long shipperId, Double latitude, Double longitude, Boolean isOnline,
        long timestamp, UUID eventId, Long deliveryId, Double accuracy, Double speed, Double heading,
        String source, String rawPayload) {}
