package com.delivery.tracking.application.api;
import java.time.LocalDateTime;
public record ShipperIdentityMapping(Long principalId, Long legacyUserId, Long shipperId,
        Long mappingVersion, LocalDateTime updatedAt) {}
