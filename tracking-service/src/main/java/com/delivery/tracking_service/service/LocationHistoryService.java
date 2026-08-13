package com.delivery.tracking_service.service;

import com.delivery.tracking_service.dto.event.ShipperLocationUpdatedEvent;
import com.delivery.tracking_service.entity.LocationHistoryReceipt;
import com.delivery.tracking_service.entity.ShipperLocationHistory;
import com.delivery.tracking_service.repository.LocationHistoryReceiptRepository;
import com.delivery.tracking_service.repository.ShipperLocationHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class LocationHistoryService {

    private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(10);
    private static final double SAMPLE_DISTANCE_METRES = 25.0;
    private final ShipperLocationHistoryRepository history;
    private final LocationHistoryReceiptRepository receipts;
    private final int maxQuerySize;
    private final String dataSourceUrl;

    public LocationHistoryService(ShipperLocationHistoryRepository history,
                                  LocationHistoryReceiptRepository receipts,
                                  @Value("${app.location-history.max-query-size:500}") int maxQuerySize,
                                  @Value("${spring.datasource.url:}") String dataSourceUrl) {
        this.history = history;
        this.receipts = receipts;
        this.maxQuerySize = Math.max(1, Math.min(maxQuerySize, 500));
        this.dataSourceUrl = dataSourceUrl;
    }

    @Transactional
    public LocationHistoryReceipt.Outcome record(ShipperLocationUpdatedEvent event, String rawPayload) {
        validateIdentity(event);
        requirePayload(rawPayload);
        Instant occurredAt = Instant.ofEpochMilli(event.getTimestamp());
        String fingerprint = fingerprint(rawPayload);

        LocationHistoryReceipt existing = receipts.findById(event.getEventId()).orElse(null);
        if (existing != null) {
            return exactReplay(existing, event, occurredAt, fingerprint);
        }
        if (claim(event, occurredAt, fingerprint) == 0) {
            existing = receipts.findById(event.getEventId()).orElseThrow(() ->
                    new IllegalStateException("location-history receipt conflict resolved without a committed row"));
            return exactReplay(existing, event, occurredAt, fingerprint);
        }

        if (event.getDeliveryId() == null) {
            return complete(event.getEventId(), LocationHistoryReceipt.Outcome.NO_DELIVERY);
        }
        if (!Boolean.TRUE.equals(event.getIsOnline())) {
            return complete(event.getEventId(), LocationHistoryReceipt.Outcome.OFFLINE_TOMBSTONE);
        }
        validateCoordinates(event);

        BigDecimal latitude = coordinate(event.getLatitude());
        BigDecimal longitude = coordinate(event.getLongitude());
        var previous = history
                .findTopByDeliveryIdAndShipperIdAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(
                        event.getDeliveryId(), event.getShipperId(), occurredAt);
        var next = history
                .findTopByDeliveryIdAndShipperIdAndOccurredAtGreaterThanEqualOrderByOccurredAtAscIdAsc(
                        event.getDeliveryId(), event.getShipperId(), occurredAt);

        boolean keep = previous.map(point -> separated(point, occurredAt, latitude, longitude))
                .orElse(true)
                && next.map(point -> separated(point, occurredAt, latitude, longitude))
                .orElse(true);
        if (!keep) {
            return complete(event.getEventId(), LocationHistoryReceipt.Outcome.SAMPLED_OUT);
        }

        history.save(new ShipperLocationHistory(
                event.getEventId(), event.getDeliveryId(), event.getShipperId(), occurredAt,
                latitude, longitude, telemetry(event.getAccuracy()), telemetry(event.getSpeed()),
                telemetry(event.getHeading()), normalizedSource(event.getSource())));
        return complete(event.getEventId(), LocationHistoryReceipt.Outcome.PERSISTED);
    }

    @Transactional(readOnly = true)
    public List<ShipperLocationHistory> byDelivery(long deliveryId, int requestedSize) {
        if (deliveryId <= 0) throw new IllegalArgumentException("deliveryId must be positive");
        int size = Math.max(1, Math.min(requestedSize, maxQuerySize));
        return history.findByDeliveryIdOrderByOccurredAtAscIdAsc(
                deliveryId, PageRequest.of(0, size));
    }

    @Transactional
    public CleanupResult cleanup(Instant cutoff) {
        int historyRows = history.deleteOlderThan(cutoff);
        int receiptRows = receipts.deleteOlderThan(cutoff);
        return new CleanupResult(historyRows, receiptRows);
    }

    private int claim(ShipperLocationUpdatedEvent event, Instant occurredAt, String fingerprint) {
        if (dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")) {
            return receipts.claimIfAbsentH2(event.getEventId(), event.getDeliveryId(), event.getShipperId(),
                    occurredAt, LocationHistoryReceipt.Outcome.PENDING.name(), fingerprint);
        }
        return receipts.claimIfAbsentPostgres(event.getEventId(), event.getDeliveryId(), event.getShipperId(),
                occurredAt, LocationHistoryReceipt.Outcome.PENDING.name(), fingerprint);
    }

    private LocationHistoryReceipt.Outcome complete(UUID eventId, LocationHistoryReceipt.Outcome outcome) {
        if (receipts.completeClaim(eventId, outcome) != 1) {
            throw new IllegalStateException("location-history receipt claim was not pending at completion");
        }
        return outcome;
    }

    private LocationHistoryReceipt.Outcome exactReplay(LocationHistoryReceipt existing,
                                                        ShipperLocationUpdatedEvent event,
                                                        Instant occurredAt,
                                                        String fingerprint) {
        if (!Objects.equals(existing.getDeliveryId(), event.getDeliveryId())
                || !Objects.equals(existing.getShipperId(), event.getShipperId())
                || !Objects.equals(existing.getOccurredAt(), occurredAt)) {
            throw new IllegalArgumentException("location-history eventId replay has contradictory identity");
        }
        // Pre-fingerprint receipts are retained for their bounded 90-day
        // support-history window. They can prove immutable identity but cannot
        // prove complete raw payload equality; all post-migration receipts are
        // strict raw-payload fences.
        if (existing.getPayloadFingerprint() != null
                && !existing.getPayloadFingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("location-history eventId replay has contradictory payload");
        }
        if (existing.getOutcome() == LocationHistoryReceipt.Outcome.PENDING) {
            throw new IllegalStateException("location-history receipt remained pending after commit");
        }
        return existing.getOutcome();
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

    private boolean separated(ShipperLocationHistory point, Instant occurredAt,
                              BigDecimal latitude, BigDecimal longitude) {
        Duration elapsed = Duration.between(point.getOccurredAt(), occurredAt).abs();
        return elapsed.compareTo(SAMPLE_INTERVAL) >= 0
                || distanceMetres(point.getLatitude().doubleValue(), point.getLongitude().doubleValue(),
                                  latitude.doubleValue(), longitude.doubleValue()) >= SAMPLE_DISTANCE_METRES;
    }

    private double distanceMetres(double lat1, double lon1, double lat2, double lon2) {
        double latDelta = Math.toRadians(lat2 - lat1);
        double lonDelta = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2);
        return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void validateIdentity(ShipperLocationUpdatedEvent event) {
        if (event == null || event.getEventId() == null || event.getShipperId() == null
                || event.getShipperId() <= 0 || event.getTimestamp() <= 0
                || event.getTimestamp() > System.currentTimeMillis() + Duration.ofMinutes(5).toMillis()) {
            throw new IllegalArgumentException("Stable event identity, shipper and timestamp are required");
        }
        if (event.getDeliveryId() != null && event.getDeliveryId() <= 0) {
            throw new IllegalArgumentException("deliveryId must be positive when present");
        }
    }

    private void validateCoordinates(ShipperLocationUpdatedEvent event) {
        if (event.getLatitude() == null || !Double.isFinite(event.getLatitude())
                || event.getLatitude() < -90 || event.getLatitude() > 90
                || event.getLongitude() == null || !Double.isFinite(event.getLongitude())
                || event.getLongitude() < -180 || event.getLongitude() > 180) {
            throw new IllegalArgumentException("Online history event requires valid coordinates");
        }
    }

    private BigDecimal coordinate(Double value) {
        return BigDecimal.valueOf(value).setScale(5, RoundingMode.HALF_UP);
    }

    private BigDecimal telemetry(Double value) {
        if (value == null) return null;
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Telemetry must be finite");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizedSource(String source) {
        if (source == null || source.isBlank()) return "UNKNOWN";
        return source.substring(0, Math.min(source.length(), 32));
    }

    public record CleanupResult(int historyRows, int receiptRows) {}
}
