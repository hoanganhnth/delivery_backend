package com.delivery.auth.application.api;
import java.util.UUID;
import java.util.Optional;
import java.time.LocalDateTime;
public interface IdentityInboxPort {
    Optional<Receipt> find(UUID eventId);
    void save(Receipt receipt);
    record Receipt(UUID eventId, String eventType, Long principalId, String fingerprint, LocalDateTime processedAt) {}
}
