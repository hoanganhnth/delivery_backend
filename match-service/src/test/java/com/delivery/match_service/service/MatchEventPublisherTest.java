package com.delivery.match_service.service;

import com.delivery.match_service.common.constants.KafkaTopicConstants;
import com.delivery.match_service.dto.event.ShipperFoundEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchEventPublisherTest {

    @Test
    void publishesWithStableMetadataAndOrderKey() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        MatchEventPublisher publisher = new MatchEventPublisher(kafkaTemplate, new ObjectMapper().findAndRegisterModules());
        ShipperFoundEvent event = new ShipperFoundEvent(11L, 22L, List.of());
        event.setEventId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa").toString());
        when(kafkaTemplate.send(eq(KafkaTopicConstants.SHIPPER_FOUND_TOPIC), eq("22"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishShipperFoundEvent(event);

        verify(kafkaTemplate).send(eq(KafkaTopicConstants.SHIPPER_FOUND_TOPIC), eq("22"),
                org.mockito.ArgumentMatchers.contains(event.getEventId()));
    }

    @Test
    void brokerFailurePropagatesSoCommandCanRetry() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        MatchEventPublisher publisher = new MatchEventPublisher(kafkaTemplate, new ObjectMapper().findAndRegisterModules());
        ShipperFoundEvent event = new ShipperFoundEvent(11L, 22L, List.of());
        event.setEventId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb").toString());
        when(kafkaTemplate.send(eq(KafkaTopicConstants.SHIPPER_FOUND_TOPIC), eq("22"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        assertThatThrownBy(() -> publisher.publishShipperFoundEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish");
    }

    @Test
    void refusesToInventEventIdentityAtPublishTime() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        MatchEventPublisher publisher = new MatchEventPublisher(kafkaTemplate, new ObjectMapper().findAndRegisterModules());
        ShipperFoundEvent event = new ShipperFoundEvent(11L, 22L, List.of());

        assertThatThrownBy(() -> publisher.publishShipperFoundEvent(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stable eventId");
    }
}
