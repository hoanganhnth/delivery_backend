package com.delivery.tracking.application.api;
import java.util.UUID;
public record ApplyShipperIdentityCommand(UUID eventId, String eventType, Long principalId,
        Long legacyUserId, Long shipperId, long mappingVersion, String rawPayload) {}
