package com.delivery.tracking.application;

import com.delivery.tracking.application.api.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

/** Canonical replay, stale-version and mapping-gap decisions, independent of Kafka/JPA. */
public final class DefaultShipperIdentityInboxUseCase implements ShipperIdentityInboxUseCase {
    private final ShipperIdentityInboxStorePort store;
    private final Clock clock;
    public DefaultShipperIdentityInboxUseCase(ShipperIdentityInboxStorePort store) {
        this(store, Clock.systemDefaultZone());
    }
    public DefaultShipperIdentityInboxUseCase(ShipperIdentityInboxStorePort store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }
    @Override public void apply(ApplyShipperIdentityCommand command) {
        if (command == null || command.eventId() == null
                || !"shipper.identity.upserted".equals(command.eventType())
                || command.principalId() == null || command.legacyUserId() == null
                || command.shipperId() == null || command.mappingVersion() < 1) {
            throw new IllegalArgumentException("Invalid shipper identity event");
        }
        Objects.requireNonNull(command.rawPayload(), "rawPayload");
        String fingerprint = fingerprint(command.rawPayload());
        store.atomically(command.eventId(), command.principalId(), () -> applyLocked(command, fingerprint));
    }
    private void applyLocked(ApplyShipperIdentityCommand event, String fingerprint) {
        var prior = store.receipt(event.eventId()).orElse(null);
        if (prior != null) {
            if (!prior.eventType().equals(event.eventType()) || !prior.principalId().equals(event.principalId())
                    || !prior.payloadFingerprint().equals(fingerprint)) {
                throw new IllegalStateException("Conflicting shipper identity event reuse");
            }
            return;
        }
        var projection = store.mapping(event.principalId()).orElse(null);
        if (projection == null || projection.mappingVersion() == null
                || event.mappingVersion() >= projection.mappingVersion()) {
            if (projection != null && projection.mappingVersion() != null
                    && event.mappingVersion() > projection.mappingVersion() + 1) {
                throw new IllegalStateException("Shipper identity mapping version gap");
            }
            store.saveMapping(new ShipperIdentityMapping(event.principalId(), event.legacyUserId(),
                    event.shipperId(), event.mappingVersion(), LocalDateTime.now(clock)));
        }
        store.saveReceipt(new ShipperIdentityReceipt(event.eventId(), event.eventType(), event.principalId(),
                fingerprint, LocalDateTime.now(clock)));
    }
    private static String fingerprint(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
