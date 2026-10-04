package com.delivery.tracking.application.api;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record LocationHistoryPoint(UUID eventId, Long deliveryId, Long shipperId, Instant occurredAt,
        BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy, BigDecimal speed,
        BigDecimal heading, String source) {}
