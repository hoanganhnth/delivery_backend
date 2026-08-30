package com.delivery.tracking_service.service;

import com.delivery.tracking_service.dto.event.ShipperLocationUpdatedEvent;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperDeliveryAssignmentStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;
import com.delivery.identity.contracts.SimulationContext;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShipperLocationEventPublisherTest {

    @Test
    void enrichedEventCarriesRedisAssignmentAndTelemetryWithoutHistoryWrite() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        ShipperDeliveryAssignmentStore assignments = mock(ShipperDeliveryAssignmentStore.class);
        when(assignments.activeDelivery(7L)).thenReturn(Optional.of(100L));
        var metadata = new org.apache.kafka.clients.producer.RecordMetadata(
                new org.apache.kafka.common.TopicPartition("shipper.location-updated", 0),
                1L, 0, System.currentTimeMillis(), 1, 1);
        var sendResult = new org.springframework.kafka.support.SendResult<String, Object>(
                new org.apache.kafka.clients.producer.ProducerRecord<>(
                        "shipper.location-updated", "7", new Object()), metadata);
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        ShipperLocationResponse location = new ShipperLocationResponse();
        location.setShipperId(7L);
        location.setLatitude(10.7);
        location.setLongitude(106.6);
        location.setAccuracy(4.2);
        location.setIsOnline(true);

        new ShipperLocationEventPublisher(kafka, assignments)
                .publishLocationUpdate(location, "WEBSOCKET");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("shipper.location-updated"), eq("7"), event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(ShipperLocationUpdatedEvent.class, value -> {
            assertThat(value.getEventId()).isNotNull();
            assertThat(value.getDeliveryId()).isEqualTo(100L);
            assertThat(value.getAccuracy()).isEqualTo(4.2);
            assertThat(value.getSource()).isEqualTo("WEBSOCKET");
        });
    }

    @Test
    void brokerFailureIsVisibleToLocationCommand() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        CompletableFuture<org.springframework.kafka.support.SendResult<String, Object>> failed =
                new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafka.send(anyString(), anyString(), any())).thenReturn(failed);

        ShipperLocationEventPublisher publisher = new ShipperLocationEventPublisher(kafka);

        assertThrows(IllegalStateException.class,
                () -> publisher.publishLocationUpdate(7L, 10.7, 106.6, true));
    }

    @Test
    void locationEventCarriesTheServerOwnedSimulationContext() {
        @SuppressWarnings("unchecked") KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        var metadata = new org.apache.kafka.clients.producer.RecordMetadata(
                new org.apache.kafka.common.TopicPartition("shipper.location-updated", 0), 1L, 0,
                System.currentTimeMillis(), 1, 1);
        var result = new org.springframework.kafka.support.SendResult<String, Object>(
                new org.apache.kafka.clients.producer.ProducerRecord<>("shipper.location-updated", "7", new Object()), metadata);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(result));
        ShipperLocationResponse location = new ShipperLocationResponse();
        location.setShipperId(7L); location.setLatitude(10.7); location.setLongitude(106.6); location.setIsOnline(true);
        SimulationContext context = new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,
                UUID.randomUUID(), UUID.randomUUID(), 2L);

        new ShipperLocationEventPublisher(kafka).publishLocationUpdate(location, "REST", context);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("shipper.location-updated"), eq("7"), event.capture());
        assertThat(((ShipperLocationUpdatedEvent) event.getValue()).getSimulationContext()).isEqualTo(context);
    }
}
