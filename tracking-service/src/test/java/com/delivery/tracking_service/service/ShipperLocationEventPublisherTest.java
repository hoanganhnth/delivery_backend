package com.delivery.tracking_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShipperLocationEventPublisherTest {

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
}
