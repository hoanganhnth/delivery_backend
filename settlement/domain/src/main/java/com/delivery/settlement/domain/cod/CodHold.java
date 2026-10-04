package com.delivery.settlement.domain.cod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable hold snapshot. Transition policy mirrors the existing service. */
public record CodHold(UUID holdId, UUID offerId, Long orderId, Long deliveryId, Long shipperId,
        UUID matchingSessionId, UUID waveId, BigDecimal amount, Status status, LocalDateTime expiresAt,
        UUID eventId, String idempotencyKey, LocalDateTime createdAt, LocalDateTime committedAt,
        LocalDateTime releasedAt, LocalDateTime consumedAt) {
    public enum Status { HELD, COMMITTED, RELEASED, EXPIRED, CONSUMED }
    public enum Stamp { NONE, COMMITTED, RELEASED, CONSUMED }
    public record Transition(Status target, boolean releaseReservation, Stamp stamp, boolean unchanged) {}

    public void requireReusable(LocalDateTime now) {
        if (status != Status.HELD || (expiresAt != null && !expiresAt.isAfter(now))) {
            throw new IllegalStateException("COD hold idempotency key is no longer reusable");
        }
    }
    public Transition decideTransition(Status target, LocalDateTime now) {
        if (status == target) return new Transition(target, false, Stamp.NONE, true);
        if (status == Status.HELD && target != Status.COMMITTED && target != Status.RELEASED && target != Status.EXPIRED) {
            throw new IllegalStateException("Invalid COD hold transition");
        }
        if (status == Status.COMMITTED && target != Status.CONSUMED && target != Status.RELEASED) {
            throw new IllegalStateException("Committed COD hold can only be released or consumed");
        }
        if (status == Status.HELD && target == Status.COMMITTED && expiresAt != null && !expiresAt.isAfter(now)) {
            return new Transition(Status.EXPIRED, true, Stamp.RELEASED, false);
        }
        // Keep existing terminal-state behavior until a separate policy change is authorized.
        if (target == Status.RELEASED || target == Status.EXPIRED) return new Transition(target, true, Stamp.RELEASED, false);
        if (target == Status.COMMITTED) return new Transition(target, false, Stamp.COMMITTED, false);
        if (target == Status.CONSUMED) return new Transition(target, true, Stamp.CONSUMED, false);
        return new Transition(target, false, Stamp.NONE, false);
    }
    public CodHold updated(Status next, LocalDateTime committed, LocalDateTime released, LocalDateTime consumed) {
        return new CodHold(holdId, offerId, orderId, deliveryId, shipperId, matchingSessionId, waveId,
                amount, next, expiresAt, eventId, idempotencyKey, createdAt, committed, released, consumed);
    }
}
