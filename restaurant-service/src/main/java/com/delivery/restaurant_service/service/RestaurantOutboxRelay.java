package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.entity.RestaurantOutboxEvent;
import com.delivery.restaurant_service.repository.RestaurantOutboxEventRepository;
import com.delivery.observability.OutboxTraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class RestaurantOutboxRelay {
    private final RestaurantOutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    @Value("${app.outbox.batch-size:50}") private int batchSize;
    @Value("${app.outbox.send-timeout-seconds:10}") private long sendTimeoutSeconds;
    @Value("${app.outbox.max-attempts:10}") private int maxAttempts;

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    @Transactional
    public void relayPendingEvents() {
        int boundedBatch = Math.max(1, Math.min(batchSize, 500));
        for (RestaurantOutboxEvent event : repository.lockNextBatch(boundedBatch)) {
            try {
                ProducerRecord<String, Object> record = new ProducerRecord<>(event.getTopic(),
                        event.getEventKey(), objectMapper.readTree(event.getPayload()));
                record.headers().add("eventId", event.getEventId().toString().getBytes(StandardCharsets.UTF_8));
                record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
                try (Scope ignored = OutboxTraceContext.open(event.getTraceparent())) {
                    kafkaTemplate.send(record).get(Math.max(1, Math.min(sendTimeoutSeconds, 60)), TimeUnit.SECONDS);
                }
                event.setStatus(RestaurantOutboxEvent.Status.SENT);
                event.setSentAt(LocalDateTime.now());
                event.setLastError(null);
            } catch (Exception e) {
                int attempts = event.getAttempts() + 1;
                event.setAttempts(attempts);
                event.setLastError(abbreviate(e.getMessage()));
                if (attempts >= Math.max(1, Math.min(maxAttempts, 100))) {
                    event.setStatus(RestaurantOutboxEvent.Status.DEAD);
                    log.error("Restaurant outbox event {} is DEAD after {} attempts",
                            event.getEventId(), attempts, e);
                } else {
                    long delay = Math.min(300L, 1L << Math.min(Math.max(attempts - 1, 0), 8));
                    event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delay));
                    log.warn("Restaurant outbox event {} retry {} scheduled in {}s",
                            event.getEventId(), attempts, delay);
                }
            }
            repository.save(event);
        }
    }

    private String abbreviate(String message) {
        if (message == null) return "Unknown Kafka publish failure";
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }

}
