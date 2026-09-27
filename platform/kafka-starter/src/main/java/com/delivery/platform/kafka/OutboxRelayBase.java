package com.delivery.platform.kafka;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reusable poll/publish/mark relay. The database update happens only after
 * Kafka acknowledges the record; a failed publish leaves the row pending, and
 * a failure after publish can safely cause a duplicate on the next poll. That
 * is the intentional at-least-once boundary for service-owned outbox tables.
 *
 * @param <E> service-owned outbox entity type
 */
public abstract class OutboxRelayBase<E> {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${platform.kafka.outbox.batch-size:100}")
    private int configuredBatchSize = 100;

    @Value("${platform.kafka.outbox.send-timeout-seconds:10}")
    private long configuredSendTimeoutSeconds = 10L;

    protected OutboxRelayBase(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${platform.kafka.outbox.poll-delay-ms:1000}")
    @Transactional
    public void relay() {
        relayBatch(configuredBatchSize);
    }

    /** Executes one bounded poll and returns the number of marked events. */
    public int relayBatch(int requestedBatchSize) {
        int batchSize = Math.max(1, Math.min(requestedBatchSize, 1_000));
        List<E> events = pollPending(batchSize);
        if (events == null || events.isEmpty()) {
            return 0;
        }

        int published = 0;
        for (E event : events) {
            try {
                kafkaTemplate.send(toProducerRecord(event))
                        .get(Math.max(1L, Math.min(configuredSendTimeoutSeconds, 60L)), TimeUnit.SECONDS);
                markPublished(event);
                published++;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                onPublishFailure(event, interrupted);
                break;
            } catch (Exception failure) {
                onPublishFailure(event, failure);
            }
        }
        return published;
    }

    /** Poll pending rows in an order that preserves the service's outbox contract. */
    protected abstract List<E> pollPending(int batchSize);

    /** Convert a service-owned row to the durable Kafka record it publishes. */
    protected abstract ProducerRecord<String, Object> toProducerRecord(E event);

    /** Mark a row only after the corresponding Kafka send has completed. */
    protected abstract void markPublished(E event);

    /** Hook for service metrics or retry scheduling; the row remains unmarked by default. */
    protected void onPublishFailure(E event, Exception failure) {
        logger.warn("Kafka outbox publish failed for {}; event remains pending",
                event == null ? "unknown event" : event.getClass().getSimpleName());
    }
}
