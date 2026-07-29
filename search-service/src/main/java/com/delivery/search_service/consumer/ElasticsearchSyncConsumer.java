package com.delivery.search_service.consumer;

import com.delivery.search_service.document.DishDocument;
import com.delivery.search_service.document.RestaurantDocument;
import com.delivery.search_service.document.EntitySyncCheckpoint;
import com.delivery.search_service.dto.EntitySyncEvent;
import com.delivery.search_service.repository.DishSearchRepository;
import com.delivery.search_service.repository.RestaurantSearchRepository;
import com.delivery.search_service.repository.EntitySyncCheckpointRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchSyncConsumer {

    private final ObjectProvider<RestaurantSearchRepository> restaurantRepository;
    private final ObjectProvider<DishSearchRepository> dishRepository;
    private final EntitySyncCheckpointRepository checkpointRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "entity-sync", groupId = "search-service-group")
    public void consumeEntitySyncEvent(EntitySyncEvent event) {
        validateEvent(event);
        if (isSuperseded(event)) {
            log.info("Skipping stale entity-sync event {} for {}:{}",
                    event.getEventId(), event.getEntityType(), event.getEntityId());
            return;
        }
        log.info("Received sync event for type: {}, action: {}, id: {}", 
                event.getEntityType(), event.getAction(), event.getEntityId());

        try {
            switch (event.getEntityType().toUpperCase()) {
                case "RESTAURANT":
                    handleRestaurantSync(event);
                    break;
                case "DISH":
                    handleDishSync(event);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown entity type: " + event.getEntityType());
            }
        } catch (Exception e) {
            log.error("Error processing sync event: {}", event, e);
            throw new IllegalStateException("Failed to synchronize search entity", e);
        }
    }

    private boolean isSuperseded(EntitySyncEvent event) {
        String checkpointId = event.getEntityType().toUpperCase(java.util.Locale.ROOT)
                + ":" + event.getEntityId();
        String fingerprint = fingerprint(event);
        EntitySyncCheckpoint existing = checkpointRepository.findById(checkpointId).orElse(null);
        if (existing != null) {
            if (existing.getEventId().equals(event.getEventId().toString())) {
                requireExactReplay(existing, event, fingerprint, checkpointId);
                return false;
            }
            int ordering = existing.getOccurredAt().compareTo(event.getOccurredAt());
            if (ordering > 0) return true;
            if (ordering == 0) {
                throw new IllegalArgumentException(
                        "Conflicting entity-sync events share the same occurredAt for " + checkpointId);
            }
        }

        // Claim the version before applying the document mutation. If the mutation
        // fails, the exact same event is allowed through on Kafka retry; older events
        // remain fenced out.
        checkpointRepository.save(EntitySyncCheckpoint.builder()
                .id(checkpointId)
                .eventId(event.getEventId().toString())
                .occurredAt(event.getOccurredAt())
                .action(event.getAction().toUpperCase(java.util.Locale.ROOT))
                .payloadFingerprint(fingerprint)
                .build());
        return false;
    }

    private void requireExactReplay(
            EntitySyncCheckpoint existing,
            EntitySyncEvent event,
            String fingerprint,
            String checkpointId) {
        String canonicalAction = event.getAction().toUpperCase(java.util.Locale.ROOT);
        if (!existing.getOccurredAt().equals(event.getOccurredAt())
                || !canonicalAction.equals(existing.getAction())) {
            throw new IllegalArgumentException(
                    "entity-sync eventId replay has contradictory metadata for " + checkpointId);
        }
        if (existing.getPayloadFingerprint() == null) {
            // Upgrade a checkpoint written before payload fingerprints existed.
            // Metadata still has to match; the exact retry establishes the new fence.
            existing.setPayloadFingerprint(fingerprint);
            checkpointRepository.save(existing);
            return;
        }
        if (!existing.getPayloadFingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException(
                    "entity-sync eventId replay has contradictory payload for " + checkpointId);
        }
    }

    private String fingerprint(EntitySyncEvent event) {
        try {
            Map<String, Object> canonical = new TreeMap<>();
            canonical.put("action", event.getAction().toUpperCase(java.util.Locale.ROOT));
            canonical.put("entityId", event.getEntityId());
            canonical.put("entityType", event.getEntityType().toUpperCase(java.util.Locale.ROOT));
            canonical.put("occurredAt", event.getOccurredAt().toString());
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
    }

    private void handleRestaurantSync(EntitySyncEvent event) {
        RestaurantSearchRepository repository = restaurantRepository.getIfAvailable();
        if (repository == null) {
            throw new IllegalStateException("Restaurant search repository is unavailable");
        }

        if ("DELETE".equalsIgnoreCase(event.getAction())) {
            repository.deleteById(event.getEntityId());
            return;
        }
        
        Map<String, Object> payload = event.getPayload();
        if (payload != null) {
            RestaurantDocument doc = objectMapper.convertValue(payload, RestaurantDocument.class);
            doc.setId(event.getEntityId());
            repository.save(doc);
        }
    }

    private void handleDishSync(EntitySyncEvent event) {
        DishSearchRepository repository = dishRepository.getIfAvailable();
        if (repository == null) {
            throw new IllegalStateException("Dish search repository is unavailable");
        }

        if ("DELETE".equalsIgnoreCase(event.getAction())) {
            repository.deleteById(event.getEntityId());
            return;
        }
        
        Map<String, Object> payload = event.getPayload();
        if (payload != null) {
            DishDocument doc = objectMapper.convertValue(payload, DishDocument.class);
            doc.setId(event.getEntityId());
            repository.save(doc);
        }
    }

}
