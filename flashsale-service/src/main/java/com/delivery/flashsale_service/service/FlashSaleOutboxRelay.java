package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.entity.FlashSaleOutboxEvent;
import com.delivery.flashsale_service.repository.FlashSaleOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "app.flashsale.outbox-relay-enabled", havingValue = "true")
public class FlashSaleOutboxRelay {
    private final FlashSaleOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${app.flashsale.outbox-relay-ms:500}")
    @Transactional
    public void relay() {
        for (FlashSaleOutboxEvent event : repository.lockDue(FlashSaleOutboxEvent.Status.PENDING,
                LocalDateTime.now(), PageRequest.of(0, 100))) publish(event);
    }

    private void publish(FlashSaleOutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get(10, TimeUnit.SECONDS);
            event.setStatus(FlashSaleOutboxEvent.Status.SENT); event.setSentAt(LocalDateTime.now()); event.setLastError(null);
        } catch (Exception exception) {
            int attempts = event.getAttempts() + 1; event.setAttempts(attempts);
            String message = exception.getMessage() == null ? "Kafka publish failed" : exception.getMessage();
            event.setLastError(message.substring(0, Math.min(2000, message.length())));
            if (attempts >= 12) { event.setStatus(FlashSaleOutboxEvent.Status.DEAD); log.error("Flash outbox {} DEAD", event.getEventId(), exception); }
            else event.setNextAttemptAt(LocalDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(attempts, 8))));
        }
    }
}
