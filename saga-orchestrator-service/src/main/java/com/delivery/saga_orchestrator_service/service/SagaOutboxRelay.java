package com.delivery.saga_orchestrator_service.service;

import com.delivery.saga_orchestrator_service.entity.SagaOutboxEvent;
import com.delivery.saga_orchestrator_service.repository.SagaOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
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
public class SagaOutboxRelay {
    private final SagaOutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    @Value("${app.outbox.batch-size:50}") private int batchSize;
    @Value("${app.outbox.send-timeout-seconds:10}") private long sendTimeoutSeconds;
    @Value("${app.outbox.max-attempts:10}") private int maxAttempts;

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    @Transactional
    public void relayCommands() {
        for (SagaOutboxEvent event : repository.lockNextOrderedBatch(Math.max(1, Math.min(batchSize, 500)))) {
            try {
                ProducerRecord<String, Object> record = new ProducerRecord<>(event.getTopic(), event.getEventKey(),
                        objectMapper.readTree(event.getPayload()));
                record.headers().add("eventId", bytes(event.getEventId().toString()));
                record.headers().add("eventType", bytes(event.getEventType()));
                record.headers().add("aggregateId", bytes(event.getAggregateId()));
                kafkaTemplate.send(record).get(Math.max(1, Math.min(sendTimeoutSeconds, 60)), TimeUnit.SECONDS);
                event.setStatus(SagaOutboxEvent.Status.SENT);
                event.setSentAt(LocalDateTime.now());
                event.setLastError(null);
            } catch (Exception failure) {
                int attempts = event.getAttempts() + 1;
                event.setAttempts(attempts);
                event.setLastError(abbreviate(failure.getMessage()));
                if (attempts >= Math.max(1, Math.min(maxAttempts, 100))) {
                    event.setStatus(SagaOutboxEvent.Status.DEAD);
                    log.error("Saga outbox command {} is DEAD after {} attempts", event.getEventId(), attempts, failure);
                } else {
                    long delay = Math.min(300L, 1L << Math.min(Math.max(attempts - 1, 0), 8));
                    event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delay));
                    log.warn("Saga outbox command {} retry {} scheduled in {}s", event.getEventId(), attempts, delay);
                }
            }
            repository.save(event);
        }
    }

    private byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private String abbreviate(String message) {
        if (message == null) return "Unknown Kafka publish failure";
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
