package com.delivery.tracking_service.service;

import com.delivery.tracking_service.dto.event.ShipperLocationUpdatedEvent;
import com.delivery.tracking_service.entity.LocationHistoryReceipt;
import com.delivery.tracking_service.listener.LocationHistoryEventListener;
import com.delivery.tracking_service.repository.LocationHistoryReceiptRepository;
import com.delivery.tracking_service.repository.ShipperLocationHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "app.location-history.max-query-size=500"
})
@ActiveProfiles("test")
@Import(LocationHistoryService.class)
class LocationHistoryServiceIntegrationTest {

    @Autowired LocationHistoryService service;
    @Autowired ShipperLocationHistoryRepository history;
    @Autowired LocationHistoryReceiptRepository receipts;

    @Test
    void samplesRoundsAndHandlesOutOfOrderEventsAgainstBothNeighbours() {
        long base = Instant.parse("2026-07-30T01:00:00Z").toEpochMilli();
        assertThat(service.record(event(base, 10.770001, 106.700001)))
                .isEqualTo(LocationHistoryReceipt.Outcome.PERSISTED);
        assertThat(service.record(event(base + 5_000, 10.770002, 106.700002)))
                .isEqualTo(LocationHistoryReceipt.Outcome.SAMPLED_OUT);
        assertThat(service.record(event(base + 6_000, 10.771000, 106.701000)))
                .isEqualTo(LocationHistoryReceipt.Outcome.PERSISTED);
        assertThat(service.record(event(base + 3_000, 10.770003, 106.700003)))
                .isEqualTo(LocationHistoryReceipt.Outcome.SAMPLED_OUT);
        assertThat(service.record(event(base + 20_000, 10.771001, 106.701001)))
                .isEqualTo(LocationHistoryReceipt.Outcome.PERSISTED);

        var points = service.byDelivery(100L, 500);
        assertThat(points).hasSize(3);
        assertThat(points).extracting(point -> point.getOccurredAt())
                .isSorted();
        assertThat(points.get(0).getLatitude().toPlainString()).isEqualTo("10.77000");
        assertThat(receipts.count()).isEqualTo(5);
    }

    @Test
    void exactKafkaReplayAcrossListenerRestartDoesNotDuplicateHistory() throws Exception {
        ShipperLocationUpdatedEvent event = event(
                Instant.parse("2026-07-30T02:00:00Z").toEpochMilli(), 10.77, 106.70);
        String payload = new ObjectMapper().writeValueAsString(event);
        Acknowledgment first = mock(Acknowledgment.class);
        new LocationHistoryEventListener(new ObjectMapper(), service).handle(payload, first);

        Acknowledgment replay = mock(Acknowledgment.class);
        new LocationHistoryEventListener(new ObjectMapper(), service).handle(payload, replay);

        assertThat(history.count()).isEqualTo(1);
        assertThat(receipts.count()).isEqualTo(1);
        verify(first).acknowledge();
        verify(replay).acknowledge();
    }

    @Test
    void cleanupDeletesHistoryAndReceiptsOlderThanRetentionCutoff() {
        service.record(event(Instant.parse("2025-01-01T00:00:00Z").toEpochMilli(), 10.77, 106.70));

        var result = service.cleanup(Instant.parse("2027-01-01T00:00:00Z"));

        assertThat(result.historyRows()).isEqualTo(1);
        assertThat(result.receiptRows()).isEqualTo(1);
        assertThat(history.count()).isZero();
        assertThat(receipts.count()).isZero();
    }

    @Test
    void eventWithoutAssignmentIsReceiptedButCannotBeMisattributed() {
        ShipperLocationUpdatedEvent event = event(
                Instant.parse("2026-07-30T03:00:00Z").toEpochMilli(), 10.77, 106.70);
        event.setDeliveryId(null);

        assertThat(service.record(event)).isEqualTo(LocationHistoryReceipt.Outcome.NO_DELIVERY);
        assertThat(history.count()).isZero();
        assertThat(receipts.count()).isEqualTo(1);
    }

    @Test
    void rollingLegacyEventGetsDeterministicReceiptWithoutLocationAttribution() {
        String legacy = "{\"shipperId\":42,\"latitude\":10.77,\"longitude\":106.7,"
                + "\"isOnline\":true,\"timestamp\":1785376800000}";
        LocationHistoryEventListener listener =
                new LocationHistoryEventListener(new ObjectMapper(), service);
        Acknowledgment first = mock(Acknowledgment.class);
        Acknowledgment replay = mock(Acknowledgment.class);

        listener.handle(legacy, first);
        listener.handle(legacy, replay);

        assertThat(history.count()).isZero();
        assertThat(receipts.count()).isEqualTo(1);
        assertThat(receipts.findAll()).singleElement()
                .satisfies(receipt -> assertThat(receipt.getOutcome())
                        .isEqualTo(LocationHistoryReceipt.Outcome.NO_DELIVERY));
        verify(first).acknowledge();
        verify(replay).acknowledge();
    }

    private ShipperLocationUpdatedEvent event(long timestamp, double latitude, double longitude) {
        return new ShipperLocationUpdatedEvent(
                42L, latitude, longitude, true, timestamp, UUID.randomUUID(), 100L,
                4.25, 8.5, 180.0, "WEBSOCKET");
    }
}
