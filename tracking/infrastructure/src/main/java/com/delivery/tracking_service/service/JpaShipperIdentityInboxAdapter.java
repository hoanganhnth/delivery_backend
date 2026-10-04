package com.delivery.tracking_service.service;

import com.delivery.tracking.application.api.*;
import com.delivery.tracking_service.entity.ShipperIdentityInboxReceipt;
import com.delivery.tracking_service.entity.ShipperIdentityProjection;
import com.delivery.tracking_service.repository.ShipperIdentityInboxReceiptRepository;
import com.delivery.tracking_service.repository.ShipperIdentityProjectionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL locks also cover absent rows; JPA writes and receipts share their transaction. */
@Component
public class JpaShipperIdentityInboxAdapter implements ShipperIdentityInboxStorePort {
    private final ShipperIdentityProjectionRepository projections;
    private final ShipperIdentityInboxReceiptRepository receipts;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public JpaShipperIdentityInboxAdapter(ShipperIdentityProjectionRepository projections,
            ShipperIdentityInboxReceiptRepository receipts, JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.projections = projections; this.receipts = receipts; this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }
    @Override public void atomically(UUID eventId, Long principalId, Runnable operation) {
        transactions.executeWithoutResult(status -> {
            lock("tracking-shipper-identity-event:" + eventId);
            lock("tracking-shipper-identity-principal:" + principalId);
            operation.run();
        });
    }
    private void lock(String key) {
        jdbc.execute((java.sql.Connection connection) -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, key); statement.execute();
            }
            return null;
        });
    }
    @Override public Optional<ShipperIdentityReceipt> receipt(UUID id) {
        return receipts.findById(id).map(row -> new ShipperIdentityReceipt(row.getEventId(), row.getEventType(),
                row.getPrincipalId(), row.getPayloadFingerprint(), row.getProcessedAt()));
    }
    @Override public Optional<ShipperIdentityMapping> mapping(Long principalId) {
        return projections.findById(principalId).map(row -> new ShipperIdentityMapping(row.getPrincipalId(),
                row.getLegacyUserId(), row.getShipperId(), row.getMappingVersion(), row.getUpdatedAt()));
    }
    @Override public void saveMapping(ShipperIdentityMapping mapping) {
        var row = new ShipperIdentityProjection(); row.setPrincipalId(mapping.principalId());
        row.setLegacyUserId(mapping.legacyUserId()); row.setShipperId(mapping.shipperId());
        row.setMappingVersion(mapping.mappingVersion()); row.setUpdatedAt(mapping.updatedAt());
        projections.save(row);
    }
    @Override public void saveReceipt(ShipperIdentityReceipt receipt) {
        var row = new ShipperIdentityInboxReceipt(); row.setEventId(receipt.eventId());
        row.setEventType(receipt.eventType()); row.setPrincipalId(receipt.principalId());
        row.setPayloadFingerprint(receipt.payloadFingerprint()); row.setProcessedAt(receipt.processedAt());
        receipts.save(row);
    }
}
