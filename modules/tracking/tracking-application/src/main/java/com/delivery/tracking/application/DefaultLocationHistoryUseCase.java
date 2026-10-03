package com.delivery.tracking.application;
import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.LocationHistoryOutcome;
import com.delivery.tracking.domain.LocationHistoryPolicy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
public final class DefaultLocationHistoryUseCase implements LocationHistoryUseCase {
    private final LocationHistoryStorePort store;
    private final LocationHistoryTransactionPort transactions;
    private final Clock clock;
    private final int maxQuerySize;
    private final int retentionDays;
    public DefaultLocationHistoryUseCase(LocationHistoryStorePort store, LocationHistoryTransactionPort transactions,
            int maxQuerySize, int retentionDays) {
        this(store, transactions, maxQuerySize, retentionDays, Clock.systemUTC());
    }
    public DefaultLocationHistoryUseCase(LocationHistoryStorePort store, LocationHistoryTransactionPort transactions,
            int maxQuerySize, int retentionDays, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxQuerySize = Math.max(1, Math.min(maxQuerySize, 500));
        this.retentionDays = Math.max(1, retentionDays);
    }
    @Override public LocationHistoryOutcome record(LocationHistoryCommand command) {
        return transactions.required(() -> recordInTransaction(command));
    }
    private LocationHistoryOutcome recordInTransaction(LocationHistoryCommand event) {
        String rawPayload = event == null ? null : event.rawPayload();
        validateIdentity(event);
        requirePayload(rawPayload);
        Instant occurredAt = Instant.ofEpochMilli(event.timestamp());
        String fingerprint = fingerprint(rawPayload);

        LocationHistoryReceiptFacts existing = store.receipt(event.eventId()).orElse(null);
        if (existing != null) {
            return exactReplay(existing, event, occurredAt, fingerprint);
        }
        if (store.claim(new LocationHistoryReceiptFacts(event.eventId(), event.deliveryId(), event.shipperId(),
                occurredAt, LocationHistoryOutcome.PENDING, fingerprint)) == 0) {
            existing = store.receipt(event.eventId()).orElseThrow(() ->
                    new IllegalStateException("location-history receipt conflict resolved without a committed row"));
            return exactReplay(existing, event, occurredAt, fingerprint);
        }

        if (event.deliveryId() == null) {
            return complete(event.eventId(), LocationHistoryOutcome.NO_DELIVERY);
        }
        if (!Boolean.TRUE.equals(event.isOnline())) {
            return complete(event.eventId(), LocationHistoryOutcome.OFFLINE_TOMBSTONE);
        }
        validateCoordinates(event);

        BigDecimal latitude = LocationHistoryPolicy.coordinate(event.latitude());
        BigDecimal longitude = LocationHistoryPolicy.coordinate(event.longitude());
        store.lockSampling(event.deliveryId(), event.shipperId());
        var previous = store.previous(
                        event.deliveryId(), event.shipperId(), occurredAt);
        var next = store.next(
                        event.deliveryId(), event.shipperId(), occurredAt);

        boolean keep = previous.map(point -> LocationHistoryPolicy.separated(point.occurredAt(), point.latitude(), point.longitude(), occurredAt, latitude, longitude))
                .orElse(true)
                && next.map(point -> LocationHistoryPolicy.separated(point.occurredAt(), point.latitude(), point.longitude(), occurredAt, latitude, longitude))
                .orElse(true);
        if (!keep) {
            return complete(event.eventId(), LocationHistoryOutcome.SAMPLED_OUT);
        }

        store.save(new LocationHistoryPoint(
                event.eventId(), event.deliveryId(), event.shipperId(), occurredAt,
                latitude, longitude, LocationHistoryPolicy.telemetry(event.accuracy()), LocationHistoryPolicy.telemetry(event.speed()),
                LocationHistoryPolicy.telemetry(event.heading()), LocationHistoryPolicy.normalizedSource(event.source())));
        return complete(event.eventId(), LocationHistoryOutcome.PERSISTED);
    }

    @Override public List<LocationHistoryPoint> byDelivery(long deliveryId, int requestedSize) {
        if (deliveryId <= 0) throw new IllegalArgumentException("deliveryId must be positive");
        int size = Math.max(1, Math.min(requestedSize, maxQuerySize));
        return transactions.readOnly(() -> store.byDelivery(deliveryId, size));
    }
    @Override public LocationHistoryCleanupResult cleanup(Instant cutoff) {
        return transactions.required(() -> new LocationHistoryCleanupResult(
                store.deleteHistoryOlderThan(cutoff), store.deleteReceiptsOlderThan(cutoff)));
    }
    @Override public LocationHistoryCleanupResult cleanupExpired() {
        return cleanup(Instant.now(clock).minus(retentionDays, java.time.temporal.ChronoUnit.DAYS));
    }

    private LocationHistoryOutcome complete(UUID eventId, LocationHistoryOutcome outcome) {
        if (store.complete(eventId, outcome) != 1) {
            throw new IllegalStateException("location-history receipt claim was not pending at completion");
        }
        return outcome;
    }

    private LocationHistoryOutcome exactReplay(LocationHistoryReceiptFacts existing,
                                                        LocationHistoryCommand event,
                                                        Instant occurredAt,
                                                        String fingerprint) {
        if (!Objects.equals(existing.deliveryId(), event.deliveryId())
                || !Objects.equals(existing.shipperId(), event.shipperId())
                || !Objects.equals(existing.occurredAt(), occurredAt)) {
            throw new IllegalArgumentException("location-history eventId replay has contradictory identity");
        }
        // Pre-fingerprint receipts are retained for their bounded 90-day
        // support-history window. They can prove immutable identity but cannot
        // prove complete raw payload equality; all post-migration receipts are
        // strict raw-payload fences.
        if (existing.payloadFingerprint() != null
                && !existing.payloadFingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("location-history eventId replay has contradictory payload");
        }
        if (existing.outcome() == LocationHistoryOutcome.PENDING) {
            throw new IllegalStateException("location-history receipt remained pending after commit");
        }
        return existing.outcome();
    }

    private void requirePayload(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new IllegalArgumentException("raw location payload is required");
        }
    }

    private String fingerprint(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void validateIdentity(LocationHistoryCommand event) {
        if (event == null || event.eventId() == null || event.shipperId() == null
                || event.shipperId() <= 0 || event.timestamp() <= 0
                || event.timestamp() > clock.millis() + Duration.ofMinutes(5).toMillis()) {
            throw new IllegalArgumentException("Stable event identity, shipper and timestamp are required");
        }
        if (event.deliveryId() != null && event.deliveryId() <= 0) {
            throw new IllegalArgumentException("deliveryId must be positive when present");
        }
    }

    private void validateCoordinates(LocationHistoryCommand event) {
        if (event.latitude() == null || !Double.isFinite(event.latitude())
                || event.latitude() < -90 || event.latitude() > 90
                || event.longitude() == null || !Double.isFinite(event.longitude())
                || event.longitude() < -180 || event.longitude() > 180) {
            throw new IllegalArgumentException("Online history event requires valid coordinates");
        }
    }

}
