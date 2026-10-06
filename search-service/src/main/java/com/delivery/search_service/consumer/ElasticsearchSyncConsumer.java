package com.delivery.search_service.consumer;

import com.delivery.search.contracts.EntitySyncEvent;
import com.delivery.search.application.DefaultProjectEntityUseCase;
import com.delivery.search.application.api.ProjectionInput;
import com.delivery.search.application.api.ProjectionPorts;
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
        ProjectionInput input = event == null ? null : new ProjectionInput(
                event.getEventId(), event.getOccurredAt(), event.getEntityType(), event.getAction(),
                event.getEntityId(), event.getPayload(), event.getAggregateVersion(),
                event.getDeletedAt(), event.getDeletionReason());
        new DefaultProjectEntityUseCase(new HostProjectionPorts(event)).project(input);
    }

    /** Bound to the original wire object so existing adapters and fixtures retain identity. */
    private final class HostProjectionPorts implements ProjectionPorts {
        private final EntitySyncEvent event;
        private HostProjectionPorts(EntitySyncEvent event) { this.event = event; }
        @Override public String fingerprint(ProjectionInput input) {
            return ElasticsearchSyncConsumer.this.fingerprint(event);
        }
        @Override public Claim claim(ProjectionInput input, String fingerprint) {
            EntitySyncCheckpointStore.ClaimResult result = checkpointStore.claim(event, fingerprint);
            // Existing fixtures may return null; it historically followed the write path.
            return result == null ? null : Claim.valueOf(result.name());
        }
        @Override public void write(ProjectionInput input) { projectionWriter.apply(event); }
        @Override public void stale(ProjectionInput input) {
            metrics.staleEventRejected();
            log.info("Skipping stale entity-sync event {} for {}:{}",
                    event.getEventId(), event.getEntityType(), event.getEntityId());
        }
        @Override public void received(ProjectionInput input) {
            log.info("Received sync event for type: {}, action: {}, id: {}",
                    event.getEntityType(), event.getAction(), event.getEntityId());
        }
        @Override public void tombstoneApplied() { metrics.tombstoneApplied(); }
        @Override public void replayFailed(ProjectionInput input, Exception failure) {
            metrics.projectionReplayFailure();
            log.error("Search projection failed for {}:{} event {}",
                    event.getEntityType(), event.getEntityId(), event.getEventId(), failure);
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

}
