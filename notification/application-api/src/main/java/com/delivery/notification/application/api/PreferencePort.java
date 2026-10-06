package com.delivery.notification.application.api;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PreferencePort {
    record Stored(boolean marketingEnabled, LocalDateTime updatedAt) {}
    Optional<Stored> find(Long principalId);
    int upsert(Long principalId, boolean enabled);
}
