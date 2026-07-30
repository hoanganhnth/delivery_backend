package com.delivery.order_service.service;

import com.delivery.order_service.entity.OutboxEvent;
import com.delivery.order_service.repository.OutboxEventRepository;
import com.delivery.observability.OutboxTraceContext;
import com.delivery.observability.SafeLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OrderOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final long sendTimeoutSeconds;
    private final int maxAttempts;

    public OrderOutboxRelay(
            OutboxEventRepository repository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.outbox.batch-size:50}") int batchSize,
            @Value("${app.outbox.send-timeout-seconds:10}") long sendTimeoutSeconds,
            @Value("${app.outbox.max-attempts:10}") int maxAttempts) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
        this.sendTimeoutSeconds = Math.max(1, Math.min(sendTimeoutSeconds, 60));
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 100));
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    @Transactional
    public void relayPendingEvents() {
        for (OutboxEvent event : repository.lockNextBatch(batchSize)) {
            try {
                JsonNode payload = objectMapper.readTree(event.getPayload());
                ProducerRecord<String, Object> record = new ProducerRecord<>(
                        event.getTopic(), event.getEventKey(), payload);
                record.headers().add("eventId", bytes(event.getEventId().toString()));
                record.headers().add("eventType", bytes(event.getEventType()));
                record.headers().add("aggregateId", bytes(event.getAggregateId()));
                record.headers().add("X-Correlation-Id", bytes(event.getCorrelationId()));
                try (Scope ignored = OutboxTraceContext.open(event.getTraceparent())) {
                    kafkaTemplate.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
                }
                event.setStatus(OutboxEvent.Status.SENT);
                event.setSentAt(LocalDateTime.now());
                event.setLastError(null);
            } catch (Exception e) {
                int attempts = event.getAttempts() + 1;
                event.setAttempts(attempts);
                event.setLastError(abbreviate(SafeLog.exceptionMessage(e)));
                if (attempts >= maxAttempts) {
                    event.setStatus(OutboxEvent.Status.DEAD);
                    log.error("Order outbox delivery failed: eventId={}, orderId={}, attempts={}, reason={}",
                            event.getEventId(), event.getAggregateId(), attempts, e.getClass().getSimpleName());
                } else {
                    long delaySeconds = Math.min(300L, 1L << Math.min(Math.max(attempts - 1, 0), 8));
                    event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
                    log.warn("Order outbox retry scheduled: eventId={}, orderId={}, attempt={}, delaySeconds={}, reason={}",
                            event.getEventId(), event.getAggregateId(), attempts, delaySeconds, e.getClass().getSimpleName());
                }
            }
            repository.save(event);
        }
    }

    private byte[] bytes(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    private String abbreviate(String message) {
        if (message == null) {
            return "Unknown Kafka publish failure";
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
