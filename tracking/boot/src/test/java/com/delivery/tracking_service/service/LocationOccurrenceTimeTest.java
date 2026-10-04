package com.delivery.tracking_service.service;

import com.delivery.tracking.application.*;
import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.*;
import com.delivery.tracking_service.dto.event.ShipperLocationUpdatedEvent;
import com.delivery.tracking_service.repository.ShipperLocationRepository;
import java.time.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The application observation time must survive an arbitrarily delayed publisher. */
class LocationOccurrenceTimeTest {
    private static final Instant OBSERVED = Instant.parse("2026-10-03T00:00:00.123Z");
    private static final Clock CLOCK = Clock.fixed(OBSERVED, ZoneId.of("America/New_York"));
    @Test void applicationEventUsesSnapshotTimeRatherThanPublicationTime() { update(LocationUpdateSource.APPLICATION); }
    @Test void webSocketEventUsesSnapshotTimeRatherThanPublicationTime() { update(LocationUpdateSource.WEBSOCKET); }
    @Test void offlineEventUsesAbsoluteTombstoneTimeRatherThanPublicationTimeOrLocalZone() {
        var f = new Fixture(); var adapter = new RedisShipperAvailabilityAdapter(f.store, f.publisher, f.fanout);
        new DefaultShipperAvailabilityUseCase(adapter, adapter, CLOCK).markOfflineAndBroadcast(7L);
        assertThat(f.event().getTimestamp()).isEqualTo(OBSERVED.toEpochMilli());
    }
    private void update(LocationUpdateSource source) {
        var f = new Fixture(); var adapter = new RedisLocationUpdateAdapter(f.store, f.publisher, f.fanout);
        var core = new DefaultTrackingService(adapter, adapter, CLOCK);
        var command = new UpdateLocationCommand(7, new Coordinate(10.77, 106.7), null, null, null, true, source);
        if (source == LocationUpdateSource.APPLICATION) core.updateLocation(command);
        else {
            when(f.store.cacheIfCurrentPublisher(any(), any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
            core.updatePublisherLocation(command, new PublisherLease(7, "current", 1));
        }
        assertThat(f.event().getTimestamp()).isEqualTo(OBSERVED.toEpochMilli());
    }
    private static class Fixture {
        @SuppressWarnings("unchecked") final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        final ShipperLocationRepository store = mock(ShipperLocationRepository.class);
        final LocationFanoutPublisher fanout = mock(LocationFanoutPublisher.class);
        final ShipperLocationEventPublisher publisher = new ShipperLocationEventPublisher(kafka);
        Fixture() {
            var metadata = new org.apache.kafka.clients.producer.RecordMetadata(
                    new org.apache.kafka.common.TopicPartition("shipper.location-updated", 0), 1L, 0, 1L, 1, 1);
            var result = new org.springframework.kafka.support.SendResult<String, Object>(
                    new org.apache.kafka.clients.producer.ProducerRecord<>("shipper.location-updated", "7", new Object()), metadata);
            when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(result));
        }
        ShipperLocationUpdatedEvent event() {
            var value = ArgumentCaptor.forClass(Object.class);
            verify(kafka).send(eq("shipper.location-updated"), eq("7"), value.capture());
            return (ShipperLocationUpdatedEvent) value.getValue();
        }
    }
}
