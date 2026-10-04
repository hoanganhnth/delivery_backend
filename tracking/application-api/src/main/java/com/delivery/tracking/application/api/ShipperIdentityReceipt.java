package com.delivery.tracking.application.api;
import java.time.LocalDateTime;
import java.util.UUID;
public record ShipperIdentityReceipt(UUID eventId, String eventType, Long principalId,
        String payloadFingerprint, LocalDateTime processedAt) {}
