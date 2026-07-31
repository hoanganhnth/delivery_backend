package com.delivery.promotion_service.service;

import com.delivery.promotion_service.entity.PromotionOutboxEvent;
import com.delivery.promotion_service.repository.PromotionOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.promotion.outbox-relay-enabled", havingValue = "true")
public class PromotionOutboxRelay {
    private static final int MAX_ATTEMPTS = 12;
    private final PromotionOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${app.promotion.outbox-relay-ms:500}")
    @Transactional
    public void relay() {
        List<PromotionOutboxEvent> events = repository.lockDue(PromotionOutboxEvent.Status.PENDING,
                LocalDateTime.now(), PageRequest.of(0, 100));
        for (PromotionOutboxEvent event : events) publish(event);
    }

    private void publish(PromotionOutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload())
                    .get(10, TimeUnit.SECONDS);
            event.setStatus(PromotionOutboxEvent.Status.SENT);
            event.setSentAt(LocalDateTime.now());
            event.setLastError(null);
        } catch (Exception exception) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setLastError(truncate(exception.getMessage()));
            if (attempts >= MAX_ATTEMPTS) {
                event.setStatus(PromotionOutboxEvent.Status.DEAD);
                log.error("Voucher reservation outbox event {} is DEAD after {} attempts",
                        event.getEventId(), attempts, exception);
            } else {
                long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
                event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
            }
        }
    }

    private String truncate(String message) {
        if (message == null) return "Kafka publish failed";
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
