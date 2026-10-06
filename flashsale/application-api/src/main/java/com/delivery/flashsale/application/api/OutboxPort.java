package com.delivery.flashsale.application.api;

import java.time.LocalDateTime;
import java.util.UUID;

public interface OutboxPort<R> {
    String state(R reservation); UUID reservationId(R reservation);
    boolean exists(UUID eventId); LocalDateTime now();
    void save(R reservation, UUID eventId, String eventType, LocalDateTime now);
}
