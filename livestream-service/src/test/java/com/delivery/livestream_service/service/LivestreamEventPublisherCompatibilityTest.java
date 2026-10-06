package com.delivery.livestream_service.service;

import com.delivery.livestream_service.dto.event.*;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;

class LivestreamEventPublisherCompatibilityTest {
    @Test
    void successPublishersRemainInert() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        var publisher = new LivestreamEventPublisher(kafka);
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        publisher.publishLivestreamStarted(new LivestreamStartedEvent(id, 7L, 42L, "room", now));
        publisher.publishLivestreamEnded(new LivestreamEndedEvent(id, 7L, 42L, now, now, 0L));
        publisher.publishProductPinned(new ProductPinnedEvent(id, 10L, BigDecimal.ONE, now));
        publisher.publishProductUnpinned(new ProductUnpinnedEvent(id, 10L, now));
        verifyNoInteractions(kafka);
    }
}
