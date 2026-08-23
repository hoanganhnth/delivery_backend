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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class RestaurantOutboxRelay {
    private final RestaurantOutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    @Autowired(required = false)
    private RestaurantOutboxLeaseService leaseService;
    @Value("${app.outbox.batch-size:50}") private int batchSize;
    @Value("${app.outbox.send-timeout-seconds:10}") private long sendTimeoutSeconds;
    @Value("${app.outbox.max-attempts:10}") private int maxAttempts;
    @Value("${app.outbox.lease-seconds:30}") private long leaseSeconds;

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    public void relayPendingEvents() {
        if (leaseService != null) {
            relayLeasedEvents();
            return;
        }

        // Compatibility rail for focused unit tests and older manually wired
        // instances. Spring production wiring uses the lease path above.
        int boundedBatch = Math.max(1, Math.min(batchSize, 500));
        List<RestaurantOutboxEvent> events = repository.lockNextBatch(boundedBatch);
        if (events == null) events = List.of();
        for (RestaurantOutboxEvent event : events) {
            try {
                ProducerRecord<String, Object> record = new ProducerRecord<>(event.getTopic(),
                        event.getEventKey(), objectMapper.readTree(event.getPayload()));
                record.headers().add("eventId", event.getEventId().toString().getBytes(StandardCharsets.UTF_8));
                record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
                try (Scope ignored = OutboxTraceContext.open(event.getTraceparent())) {
                    kafkaTemplate.send(record).get(Math.max(1, Math.min(sendTimeoutSeconds, 60)), TimeUnit.SECONDS);
                }
                if (leaseService == null) {
                    event.setStatus(RestaurantOutboxEvent.Status.SENT);
                    event.setSentAt(LocalDateTime.now());
                    event.setLastError(null);
                    repository.save(event);
                }
            } catch (Exception e) {
                recordLegacyFailure(event, e);
                repository.save(event);
            }
        }
    }

    /** Give every claimed event its own lease budget; see the order relay. */
    private void relayLeasedEvents() {
        int boundedBatch = Math.max(1, Math.min(batchSize, 500));
        long eventLeaseSeconds = Math.max(leaseSeconds, sendTimeoutSeconds + 10);
        for (int processed = 0; processed < boundedBatch; processed++) {
            UUID leaseToken = UUID.randomUUID();
            List<RestaurantOutboxEvent> events;
            try {
                events = leaseService.claim(1, leaseToken, eventLeaseSeconds);
            } catch (RuntimeException claimFailure) {
                log.warn("Restaurant outbox claim failed; relay will retry on next poll: reason={}",
                        claimFailure.getClass().getSimpleName());
                return;
            }
            if (events == null || events.isEmpty()) return;
            RestaurantOutboxEvent event = events.get(0);
            try {
                ProducerRecord<String, Object> record = new ProducerRecord<>(event.getTopic(),
                        event.getEventKey(), objectMapper.readTree(event.getPayload()));
                record.headers().add("eventId", event.getEventId().toString().getBytes(StandardCharsets.UTF_8));
                record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
                try (Scope ignored = OutboxTraceContext.open(event.getTraceparent())) {
                    kafkaTemplate.send(record).get(Math.max(1, Math.min(sendTimeoutSeconds, 60)), TimeUnit.SECONDS);
                }
            } catch (Exception failure) {
                safeMarkFailure(event, leaseToken, failure);
                if (failure instanceof InterruptedException) return;
                continue;
            }
            try {
                if (!leaseService.markSent(event.getId(), leaseToken)) {
                    log.warn("Restaurant outbox lease was lost after successful publish: eventId={}", event.getEventId());
                }
            } catch (RuntimeException updateFailure) {
                log.warn("Restaurant outbox state update failed after successful publish; event will be reclaimed: eventId={}, reason={}",
                        event.getEventId(), updateFailure.getClass().getSimpleName());
            }
        }
    }

    private void safeMarkFailure(RestaurantOutboxEvent event, UUID leaseToken, Exception failure) {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        try {
            if (!leaseService.markFailure(event.getId(), leaseToken, failure, maxAttempts)) {
                log.warn("Restaurant outbox lease was lost after publish failure: eventId={}", event.getEventId());
            }
        } catch (RuntimeException updateFailure) {
            log.warn("Restaurant outbox state update failed after publish failure; event will be reclaimed: eventId={}, reason={}",
                    event.getEventId(), updateFailure.getClass().getSimpleName());
        }
    }

    private void recordLegacyFailure(RestaurantOutboxEvent event, Exception failure) {
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setLastError(abbreviate(failure.getMessage()));
        if (attempts >= Math.max(1, Math.min(maxAttempts, 100))) {
            event.setStatus(RestaurantOutboxEvent.Status.DEAD);
            log.error("Restaurant outbox event {} is DEAD after {} attempts", event.getEventId(), attempts, failure);
        } else {
            long delay = Math.min(300L, 1L << Math.min(Math.max(attempts - 1, 0), 8));
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delay));
            log.warn("Restaurant outbox event {} retry {} scheduled in {}s", event.getEventId(), attempts, delay);
        }
    }

    private String abbreviate(String message) {
        if (message == null) return "Unknown Kafka publish failure";
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }

}
