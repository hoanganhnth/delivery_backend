package com.delivery.tracking.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.tracking_service.entity.LocationHistoryReceipt;
import com.delivery.tracking_service.entity.ShipperIdentityInboxReceipt;
import com.delivery.tracking_service.entity.ShipperIdentityProjection;
import com.delivery.tracking_service.entity.ShipperLocationHistory;
import com.delivery.tracking_service.repository.LocationHistoryReceiptRepository;
import com.delivery.tracking_service.repository.ShipperIdentityInboxReceiptRepository;
import com.delivery.tracking_service.repository.ShipperIdentityProjectionRepository;
import com.delivery.tracking_service.repository.ShipperLocationHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * H2 proof for the extracted Tracking persistence adapter.  This deliberately
 * exercises the module's Flyway resources and Spring Data query methods without
 * booting the executable tracking host or any Redis/Kafka infrastructure.
 */
@SpringBootTest(
        classes = TrackingJpaRepositoryH2Test.PersistenceTestApplication.class,
        properties = {
                "spring.main.web-application-type=none",
                "spring.datasource.url=jdbc:h2:mem:tracking_infrastructure;MODE=PostgreSQL;"
                        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.flyway.enabled=true"
        })
@Transactional
class TrackingJpaRepositoryH2Test {

    @SpringBootApplication(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {
            com.delivery.tracking_service.entity.LocationHistoryReceipt.class,
            com.delivery.tracking_service.entity.ShipperIdentityInboxReceipt.class,
            com.delivery.tracking_service.entity.ShipperIdentityProjection.class,
            com.delivery.tracking_service.entity.ShipperLocationHistory.class
    })
    @EnableJpaRepositories(basePackageClasses = {
            LocationHistoryReceiptRepository.class,
            ShipperIdentityInboxReceiptRepository.class,
            ShipperIdentityProjectionRepository.class,
            ShipperLocationHistoryRepository.class
    })
    static class PersistenceTestApplication {
    }

    private final Instant baseTime = Instant.parse("2026-09-28T00:00:00Z");

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired
    private LocationHistoryReceiptRepository receipts;

    @org.springframework.beans.factory.annotation.Autowired
    private ShipperIdentityInboxReceiptRepository inboxReceipts;

    @org.springframework.beans.factory.annotation.Autowired
    private ShipperLocationHistoryRepository history;

    @org.springframework.beans.factory.annotation.Autowired
    private ShipperIdentityProjectionRepository projections;

    @BeforeEach
    void clean() {
        history.deleteAll();
        receipts.deleteAll();
        inboxReceipts.deleteAll();
        projections.deleteAll();
    }

    @Test
    void flywayMigrationsArePackagedWithInfrastructureAndCreateAllOwnedTables() {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '6'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_name in ('shipper_location_history', "
                        + "'location_history_receipts', 'shipper_identity_projection', "
                        + "'shipper_identity_inbox_receipts')", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_name = 'location_history_receipts' "
                        + "and column_name = 'payload_fingerprint'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void springDataHistoryQueriesPreserveChronologicalDeliveryOrdering() {
        history.saveAllAndFlush(List.of(
                point(UUID.randomUUID(), baseTime.plusSeconds(20), "later"),
                point(UUID.randomUUID(), baseTime, "first"),
                point(UUID.randomUUID(), baseTime.plusSeconds(10), "middle")));

        var points = history.findByDeliveryIdOrderByOccurredAtAscIdAsc(91L, PageRequest.of(0, 10));

        assertThat(points).extracting(ShipperLocationHistory::getSource)
                .containsExactly("first", "middle", "later");
    }

    @Test
    void receiptClaimQueryIsIdempotentAndCompletionFencesPendingOutcome() {
        UUID eventId = UUID.randomUUID();
        assertThat(receipts.claimIfAbsentH2(eventId, 12L, 7L, baseTime,
                LocationHistoryReceipt.Outcome.PENDING.name(), "fingerprint"))
                .isEqualTo(1);
        assertThat(receipts.claimIfAbsentH2(eventId, 12L, 7L, baseTime,
                LocationHistoryReceipt.Outcome.PENDING.name(), "fingerprint"))
                .isZero();
        assertThat(receipts.completeClaim(eventId, LocationHistoryReceipt.Outcome.PERSISTED))
                .isEqualTo(1);
        assertThat(receipts.completeClaim(eventId, LocationHistoryReceipt.Outcome.SAMPLED_OUT))
                .isZero();
        assertThat(receipts.findById(eventId).orElseThrow().getOutcome())
                .isEqualTo(LocationHistoryReceipt.Outcome.PERSISTED);
    }

    @Test
    void identityProjectionMappingIsPersistedByTheExtractedRepository() {
        ShipperIdentityProjection projection = new ShipperIdentityProjection();
        projection.setPrincipalId(101L);
        projection.setLegacyUserId(202L);
        projection.setShipperId(303L);
        projection.setMappingVersion(1L);
        projection.setUpdatedAt(LocalDateTime.parse("2026-09-28T07:00:00"));

        projections.saveAndFlush(projection);

        assertThat(projections.findById(101L)).get().satisfies(found -> {
            assertThat(found.getLegacyUserId()).isEqualTo(202L);
            assertThat(found.getShipperId()).isEqualTo(303L);
            assertThat(found.getMappingVersion()).isEqualTo(1L);
        });
    }

    @Test
    void identityInboxReceiptMappingIsPersistedByTheExtractedRepository() {
        UUID eventId = UUID.randomUUID();
        ShipperIdentityInboxReceipt receipt = new ShipperIdentityInboxReceipt();
        receipt.setEventId(eventId);
        receipt.setEventType("SHIPPER_IDENTITY_UPSERTED");
        receipt.setPrincipalId(101L);
        receipt.setPayloadFingerprint("fingerprint");
        receipt.setProcessedAt(LocalDateTime.parse("2026-09-28T07:00:00"));

        inboxReceipts.saveAndFlush(receipt);

        assertThat(inboxReceipts.findById(eventId)).get().satisfies(found -> {
            assertThat(found.getEventType()).isEqualTo("SHIPPER_IDENTITY_UPSERTED");
            assertThat(found.getPrincipalId()).isEqualTo(101L);
            assertThat(found.getPayloadFingerprint()).isEqualTo("fingerprint");
        });
    }

    private ShipperLocationHistory point(UUID eventId, Instant occurredAt, String source) {
        return new ShipperLocationHistory(eventId, 91L, 7L, occurredAt,
                new BigDecimal("10.77000"), new BigDecimal("106.70000"),
                new BigDecimal("4.25"), new BigDecimal("8.50"),
                new BigDecimal("180.00"), source);
    }
}
