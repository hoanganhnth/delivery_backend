package com.delivery.tracking_service.service;
import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.LocationHistoryOutcome;
import com.delivery.tracking_service.entity.*;
import com.delivery.tracking_service.repository.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** JPA facts, atomic receipt SQL and transaction mechanics; no sampling/replay policy. */
@Component
public class JpaLocationHistoryAdapter implements LocationHistoryStorePort, LocationHistoryTransactionPort {
    private final ShipperLocationHistoryRepository history;
    private final LocationHistoryReceiptRepository receipts;
    private final boolean h2;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;
    public JpaLocationHistoryAdapter(ShipperLocationHistoryRepository history, LocationHistoryReceiptRepository receipts,
            PlatformTransactionManager manager, JdbcTemplate jdbc, @Value("${spring.datasource.url:}") String dataSourceUrl) {
        this.history = history; this.receipts = receipts; this.jdbc = jdbc;
        this.h2 = dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:");
        this.writes = new TransactionTemplate(manager);
        this.reads = new TransactionTemplate(manager); reads.setReadOnly(true);
    }
    @Override public <T> T required(Supplier<T> operation) { return writes.execute(status -> operation.get()); }
    @Override public <T> T readOnly(Supplier<T> operation) { return reads.execute(status -> operation.get()); }
    @Override public Optional<LocationHistoryReceiptFacts> receipt(UUID id) {
        return receipts.findById(id).map(row -> new LocationHistoryReceiptFacts(row.getEventId(), row.getDeliveryId(),
                row.getShipperId(), row.getOccurredAt(), LocationHistoryOutcome.valueOf(row.getOutcome().name()), row.getPayloadFingerprint()));
    }
    @Override public int claim(LocationHistoryReceiptFacts value) {
        return h2 ? receipts.claimIfAbsentH2(value.eventId(), value.deliveryId(), value.shipperId(), value.occurredAt(),
                value.outcome().name(), value.payloadFingerprint())
                : receipts.claimIfAbsentPostgres(value.eventId(), value.deliveryId(), value.shipperId(), value.occurredAt(),
                value.outcome().name(), value.payloadFingerprint());
    }
    @Override public int complete(UUID id, LocationHistoryOutcome outcome) {
        return receipts.completeClaim(id, LocationHistoryReceipt.Outcome.valueOf(outcome.name()));
    }
    @Override public void lockSampling(Long deliveryId, Long shipperId) {
        // H2 is the existing sequential fixture fallback; concurrent production proof uses PostgreSQL.
        if (h2) return;
        jdbc.execute((java.sql.Connection connection) -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, "tracking-history-sampling:" + deliveryId + ":" + shipperId);
                statement.execute();
            }
            return null;
        });
    }
    @Override public Optional<LocationHistoryPoint> previous(Long delivery, Long shipper, Instant time) {
        return history.findTopByDeliveryIdAndShipperIdAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(delivery, shipper, time).map(JpaLocationHistoryAdapter::point);
    }
    @Override public Optional<LocationHistoryPoint> next(Long delivery, Long shipper, Instant time) {
        return history.findTopByDeliveryIdAndShipperIdAndOccurredAtGreaterThanEqualOrderByOccurredAtAscIdAsc(delivery, shipper, time).map(JpaLocationHistoryAdapter::point);
    }
    @Override public void save(LocationHistoryPoint point) {
        history.save(new ShipperLocationHistory(point.eventId(), point.deliveryId(), point.shipperId(), point.occurredAt(),
                point.latitude(), point.longitude(), point.accuracy(), point.speed(), point.heading(), point.source()));
    }
    @Override public List<LocationHistoryPoint> byDelivery(long delivery, int size) {
        return history.findByDeliveryIdOrderByOccurredAtAscIdAsc(delivery, PageRequest.of(0, size)).stream()
                .map(JpaLocationHistoryAdapter::point).toList();
    }
    @Override public int deleteHistoryOlderThan(Instant cutoff) { return history.deleteOlderThan(cutoff); }
    @Override public int deleteReceiptsOlderThan(Instant cutoff) { return receipts.deleteOlderThan(cutoff); }
    private static LocationHistoryPoint point(ShipperLocationHistory row) {
        return new LocationHistoryPoint(row.getEventId(), row.getDeliveryId(), row.getShipperId(), row.getOccurredAt(),
                row.getLatitude(), row.getLongitude(), row.getAccuracy(), row.getSpeed(), row.getHeading(), row.getSource());
    }
}
