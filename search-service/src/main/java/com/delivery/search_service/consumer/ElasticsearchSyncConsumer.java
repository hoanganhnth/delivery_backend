package com.delivery.search_service.consumer;

import com.delivery.search.contracts.EntitySyncEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.delivery.observability.Phase8Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Map;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchSyncConsumer {

    private final EntitySyncCheckpointStore checkpointStore;
    private final SearchProjectionWriter projectionWriter;
    private final ObjectMapper objectMapper;
    private final Phase8Metrics metrics;

    @Autowired
    public ElasticsearchSyncConsumer(EntitySyncCheckpointStore checkpointStore,
                                     SearchProjectionWriter projectionWriter,
                                     ObjectMapper objectMapper, Phase8Metrics metrics) {
        this.checkpointStore = checkpointStore;
        this.projectionWriter = projectionWriter;
        this.objectMapper = objectMapper;
        this.metrics = java.util.Objects.requireNonNull(metrics);
    }

    /** Compatibility for focused fixtures; production uses the shared registry bean. */
    public ElasticsearchSyncConsumer(EntitySyncCheckpointStore checkpointStore,
                                     SearchProjectionWriter projectionWriter, ObjectMapper objectMapper) {
        this(checkpointStore, projectionWriter, objectMapper,
                new Phase8Metrics(new SimpleMeterRegistry(), "search-service"));
    }

    @KafkaListener(topics = "entity-sync", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeEntitySyncEvent(EntitySyncEvent event) {
        validateEvent(event);
        EntitySyncCheckpointStore.ClaimResult claim = checkpointStore.claim(event, fingerprint(event));
        if (claim == EntitySyncCheckpointStore.ClaimResult.STALE) {
            metrics.staleEventRejected();
            log.info("Skipping stale entity-sync event {} for {}:{}",
                    event.getEventId(), event.getEntityType(), event.getEntityId());
            return;
        }
        log.info("Received sync event for type: {}, action: {}, id: {}", 
                event.getEntityType(), event.getAction(), event.getEntityId());

        try {
            projectionWriter.apply(event);
            if ("DELETE".equalsIgnoreCase(event.getAction())) metrics.tombstoneApplied();
        } catch (Exception e) {
            metrics.projectionReplayFailure();
            log.error("Search projection failed for {}:{} event {}",
                    event.getEntityType(), event.getEntityId(), event.getEventId(), e);
            throw new IllegalStateException("Failed to synchronize search entity", e);
        }
    }

    private String fingerprint(EntitySyncEvent event) {
        try {
            Map<String, Object> canonical = new TreeMap<>();
            canonical.put("action", event.getAction().toUpperCase(java.util.Locale.ROOT));
            canonical.put("entityId", event.getEntityId());
            canonical.put("entityType", event.getEntityType().toUpperCase(java.util.Locale.ROOT));
            canonical.put("occurredAt", event.getOccurredAt().toString());
            canonical.put("aggregateVersion", event.getAggregateVersion());
            canonical.put("deletedAt", event.getDeletedAt());
            canonical.put("deletionReason", event.getDeletionReason());
            canonical.put("payload", event.getPayload());
            byte[] json = objectMapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsBytes(canonical);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(json));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        } catch (Exception e) {
            throw new IllegalArgumentException("entity-sync payload cannot be fingerprinted", e);
        }
    }

    private void validateEvent(EntitySyncEvent event) {
        if (event == null || event.getEventId() == null || event.getOccurredAt() == null
                || event.getEntityType() == null || event.getEntityType().isBlank()
                || event.getAction() == null || event.getAction().isBlank()
                || event.getEntityId() == null || event.getEntityId().isBlank()) {
            throw new IllegalArgumentException(
                    "stable eventId, occurredAt, entityType, action and entityId are required");
        }
        if (!java.util.Set.of("CREATE", "UPDATE", "DELETE")
                .contains(event.getAction().toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported entity action: " + event.getAction());
        }
        String entityType = event.getEntityType().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("RESTAURANT", "DISH").contains(entityType)) {
            throw new IllegalArgumentException("Unsupported entity type: " + event.getEntityType());
        }
        if (!"DELETE".equalsIgnoreCase(event.getAction()) && event.getPayload() == null) {
            throw new IllegalArgumentException("payload is required for create/update");
        }
        if (event.getAggregateVersion() != null && event.getAggregateVersion() < 1) {
            throw new IllegalArgumentException("aggregateVersion must be positive");
        }
        if ("DELETE".equalsIgnoreCase(event.getAction()) && event.getDeletedAt() != null
                && event.getDeletedAt().isAfter(event.getOccurredAt())) {
            throw new IllegalArgumentException("deletedAt cannot be after occurredAt");
        }
    }

}
