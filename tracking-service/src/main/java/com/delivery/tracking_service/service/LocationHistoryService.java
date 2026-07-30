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
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class LocationHistoryService {

    private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(10);
    private static final double SAMPLE_DISTANCE_METRES = 25.0;
    private final ShipperLocationHistoryRepository history;
    private final LocationHistoryReceiptRepository receipts;
    private final int maxQuerySize;

    public LocationHistoryService(ShipperLocationHistoryRepository history,
                                  LocationHistoryReceiptRepository receipts,
                                  @Value("${app.location-history.max-query-size:500}") int maxQuerySize) {
        this.history = history;
        this.receipts = receipts;
        this.maxQuerySize = Math.max(1, Math.min(maxQuerySize, 500));
    }

    @Transactional
    public LocationHistoryReceipt.Outcome record(ShipperLocationUpdatedEvent event) {
        validateIdentity(event);
        if (receipts.existsById(event.getEventId())) {
            return receipts.findById(event.getEventId()).orElseThrow().getOutcome();
        }
        Instant occurredAt = Instant.ofEpochMilli(event.getTimestamp());
        if (event.getDeliveryId() == null) {
            return receipt(event, occurredAt, LocationHistoryReceipt.Outcome.NO_DELIVERY);
        }
        if (!Boolean.TRUE.equals(event.getIsOnline())) {
            return receipt(event, occurredAt, LocationHistoryReceipt.Outcome.OFFLINE_TOMBSTONE);
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
            return receipt(event, occurredAt, LocationHistoryReceipt.Outcome.SAMPLED_OUT);
        }

        history.save(new ShipperLocationHistory(
                event.getEventId(), event.getDeliveryId(), event.getShipperId(), occurredAt,
                latitude, longitude, telemetry(event.getAccuracy()), telemetry(event.getSpeed()),
                telemetry(event.getHeading()), normalizedSource(event.getSource())));
        return receipt(event, occurredAt, LocationHistoryReceipt.Outcome.PERSISTED);
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

    private LocationHistoryReceipt.Outcome receipt(
            ShipperLocationUpdatedEvent event, Instant occurredAt,
            LocationHistoryReceipt.Outcome outcome) {
        receipts.save(new LocationHistoryReceipt(event.getEventId(), event.getDeliveryId(),
                event.getShipperId(), occurredAt, outcome));
        return outcome;
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
