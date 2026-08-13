package com.delivery.tracking_service.service;

import com.delivery.tracking_service.entity.LocationHistoryReceipt;
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
@Import(LocationHistoryService.class)
@Testcontainers(disabledWithoutDocker = true)
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

    @Autowired private LocationHistoryService service;
    @Autowired private ShipperLocationHistoryRepository history;
    @Autowired private LocationHistoryReceiptRepository receipts;
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
                    service.record(event, raw)));
            Future<Throwable> second = executor.submit(() -> invokeTogether(ready, start, () ->
                    service.record(event, raw)));

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
        service.record(event, raw);

        ShipperLocationUpdatedEvent contradictory = new ShipperLocationUpdatedEvent(
                event.getShipperId(), event.getLatitude(), event.getLongitude(), event.getIsOnline(),
                event.getTimestamp(), event.getEventId(), event.getDeliveryId(), event.getAccuracy(),
                event.getSpeed(), event.getHeading(), "REST");
        assertThrows(IllegalArgumentException.class,
                () -> service.record(contradictory, objectMapper.writeValueAsString(contradictory)));

        assertThat(receipts.count()).isEqualTo(1);
        assertThat(history.count()).isEqualTo(1);
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
