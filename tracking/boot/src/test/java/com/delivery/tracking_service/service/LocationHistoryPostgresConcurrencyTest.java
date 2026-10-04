package com.delivery.tracking_service.service;

import com.delivery.tracking_service.entity.LocationHistoryReceipt;
import com.delivery.tracking.application.api.LocationHistoryUseCase;
import com.delivery.tracking.application.api.LocationHistoryPoint;
import com.delivery.tracking.application.DefaultLocationHistoryUseCase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.Optional;
import static org.awaitility.Awaitility.await;
import com.delivery.tracking.domain.LocationHistoryOutcome;
import com.delivery.tracking_service.config.LocationHistoryConfiguration;
import com.delivery.tracking_service.repository.LocationHistoryReceiptRepository;
import com.delivery.tracking_service.repository.ShipperLocationHistoryRepository;
import com.delivery.tracking_service.dto.event.ShipperLocationUpdatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Proves concurrent Tracking consumers converge before Kafka ACK on PostgreSQL. */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "app.location-history.max-query-size=500"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaLocationHistoryAdapter.class, LocationHistoryConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
class LocationHistoryPostgresConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tracking_history")
            .withUsername("tracking")
            .withPassword("tracking");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private LocationHistoryUseCase service;
    @Autowired private ShipperLocationHistoryRepository history;
    @Autowired private LocationHistoryReceiptRepository receipts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        history.deleteAll();
        receipts.deleteAll();
    }

    @Test
    void concurrentExactRecordsCommitOneReceiptAndOneHistoryPoint() throws Exception {
        ShipperLocationUpdatedEvent event = event();
        String raw = objectMapper.writeValueAsString(event);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = executor.submit(() -> invokeTogether(ready, start, () ->
                    service.record(LocationHistoryCommandMapper.from(event, raw))));
            Future<Throwable> second = executor.submit(() -> invokeTogether(ready, start, () ->
                    service.record(LocationHistoryCommandMapper.from(event, raw))));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS)).isNull();
            assertThat(second.get(30, TimeUnit.SECONDS)).isNull();
        } finally {
            executor.shutdownNow();
        }

        assertThat(receipts.findAll()).singleElement().satisfies(receipt -> {
            assertThat(receipt.getOutcome()).isEqualTo(LocationHistoryReceipt.Outcome.PERSISTED);
            assertThat(receipt.getPayloadFingerprint()).hasSize(64);
        });
        assertThat(history.count()).isEqualTo(1);
    }

    @Test
    void contradictoryRawReuseIsPoisonAfterFirstReceiptCommits() throws Exception {
        ShipperLocationUpdatedEvent event = event();
        String raw = objectMapper.writeValueAsString(event);
        service.record(LocationHistoryCommandMapper.from(event, raw));

        ShipperLocationUpdatedEvent contradictory = new ShipperLocationUpdatedEvent(
                event.getShipperId(), event.getLatitude(), event.getLongitude(), event.getIsOnline(),
                event.getTimestamp(), event.getEventId(), event.getDeliveryId(), event.getAccuracy(),
                event.getSpeed(), event.getHeading(), "REST");
        assertThrows(IllegalArgumentException.class,
                () -> service.record(LocationHistoryCommandMapper.from(contradictory, objectMapper.writeValueAsString(contradictory))));

        assertThat(receipts.count()).isEqualTo(1);
        assertThat(history.count()).isEqualTo(1);
    }

    @Test
    void invalidCoordinatesAndTelemetryRollbackClaimBeforeSuccessfulRetry() throws Exception {
        ShipperLocationUpdatedEvent event = event();
        event.setLatitude(91.0);
        assertThrows(IllegalArgumentException.class, () -> service.record(
                LocationHistoryCommandMapper.from(event, objectMapper.writeValueAsString(event))));
        assertThat(receipts.existsById(event.getEventId())).isFalse();
        assertThat(history.count()).isZero();
        event.setLatitude(10.77); event.setSpeed(Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> service.record(
                LocationHistoryCommandMapper.from(event, objectMapper.writeValueAsString(event))));
        assertThat(receipts.existsById(event.getEventId())).isFalse();
        assertThat(history.count()).isZero();
        event.setSpeed(8.5);
        assertThat(service.record(LocationHistoryCommandMapper.from(event, objectMapper.writeValueAsString(event))))
                .isEqualTo(LocationHistoryOutcome.PERSISTED);
        assertThat(receipts.count()).isEqualTo(1); assertThat(history.count()).isEqualTo(1);
    }

    @Test
    void differentEventIdsCannotRacePastBothSamplingNeighbours() throws Exception {
        var firstEvent = event(); var secondEvent = event();
        secondEvent.setTimestamp(firstEvent.getTimestamp() + 1000);
        var neighboursRead = new CountDownLatch(1); var release = new CountDownLatch(1);
        var secondLockStarted = new CountDownLatch(1);
        var firstAdapter = new JpaLocationHistoryAdapter(history, receipts, transactions, jdbc, POSTGRES.getJdbcUrl()) {
            @Override public Optional<LocationHistoryPoint> next(Long delivery, Long shipper, Instant time) {
                var result = super.next(delivery, shipper, time);
                neighboursRead.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("sampling release timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                return result;
            }
        };
        var secondAdapter = new JpaLocationHistoryAdapter(history, receipts, transactions, jdbc, POSTGRES.getJdbcUrl()) {
            @Override public void lockSampling(Long delivery, Long shipper) {
                secondLockStarted.countDown(); super.lockSampling(delivery, shipper);
            }
        };
        var firstCore = new DefaultLocationHistoryUseCase(firstAdapter, firstAdapter, 500, 90);
        var secondCore = new DefaultLocationHistoryUseCase(secondAdapter, secondAdapter, 500, 90);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> firstCore.record(LocationHistoryCommandMapper.from(firstEvent,
                    objectMapper.writeValueAsString(firstEvent))));
            assertThat(neighboursRead.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> secondCore.record(LocationHistoryCommandMapper.from(secondEvent,
                    objectMapper.writeValueAsString(secondEvent))));
            assertThat(secondLockStarted.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted", Long.class)).isPositive());
            } catch (org.awaitility.core.ConditionTimeoutException missingFence) {
                throw new AssertionError("Second sampling writer was not blocked by PostgreSQL", missingFence);
            }
            assertThat(second.isDone()).isFalse();
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(LocationHistoryOutcome.PERSISTED);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(LocationHistoryOutcome.SAMPLED_OUT);
            assertThat(history.count()).isEqualTo(1);
            assertThat(receipts.count()).isEqualTo(2);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    private ShipperLocationUpdatedEvent event() {
        return new ShipperLocationUpdatedEvent(
                42L, 10.77, 106.70, true, Instant.now().toEpochMilli(), UUID.randomUUID(),
                100L, 4.25, 8.5, 180.0, "WEBSOCKET");
    }

    private Throwable invokeTogether(CountDownLatch ready, CountDownLatch start, Operation operation) {
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent Tracking test did not start");
            }
            operation.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    @FunctionalInterface
    private interface Operation {
        void run() throws Exception;
    }
}
