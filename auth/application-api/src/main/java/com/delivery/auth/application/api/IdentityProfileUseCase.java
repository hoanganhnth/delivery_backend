package com.delivery.auth.application.api;
import java.util.UUID;
public interface IdentityProfileUseCase {
    void profileCreated(Event event, String fingerprint);
    record Event(UUID eventId, String eventType, Long principalId, Long profileId, String profileType) {}
}
