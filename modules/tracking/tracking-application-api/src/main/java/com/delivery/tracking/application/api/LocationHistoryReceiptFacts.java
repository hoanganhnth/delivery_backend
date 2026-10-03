package com.delivery.tracking.application.api;
import com.delivery.tracking.domain.LocationHistoryOutcome;
import java.time.Instant;
import java.util.UUID;
public record LocationHistoryReceiptFacts(UUID eventId, Long deliveryId, Long shipperId,
        Instant occurredAt, LocationHistoryOutcome outcome, String payloadFingerprint) {}
