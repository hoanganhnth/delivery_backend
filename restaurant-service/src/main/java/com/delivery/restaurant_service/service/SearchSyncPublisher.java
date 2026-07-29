package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.event.EntitySyncEvent;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.RestaurantOutboxEvent;
import com.delivery.restaurant_service.repository.RestaurantOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchSyncPublisher {

    private final RestaurantOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private static final String TOPIC = "entity-sync";

    @Value("${app.search-sync.enabled:true}")
    private boolean enabled;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishRestaurantChange(Restaurant restaurant, String action) {
        if (!enabled) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            if (!"DELETE".equals(action)) {
                // Map only what search service needs
                payload.put("id", restaurant.getId().toString());
                payload.put("name", restaurant.getName());
                payload.put("description", restaurant.getDescription());
                payload.put("rating", restaurant.getRating());
                payload.put("imageUrl", restaurant.getImage());
            }

            EntitySyncEvent event = EntitySyncEvent.builder()
                    .eventId(UUID.randomUUID())
                    .occurredAt(LocalDateTime.now())
                    .entityType("RESTAURANT")
                    .action(action)
                    .entityId(restaurant.getId().toString())
                    .payload(payload)
                    .build();

            store(event, "SEARCH_RESTAURANT_" + action);
            log.info("Stored restaurant search sync event: action={}, id={}", action, restaurant.getId());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot store restaurant search sync event", e);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishDishChange(MenuItem dish, String action) {
        if (!enabled) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            if (!"DELETE".equals(action)) {
                payload.put("id", dish.getId().toString());
                payload.put("name", dish.getName());
                payload.put("description", dish.getDescription());
                payload.put("price", dish.getPrice());
                if (dish.getRestaurant() != null) {
                    payload.put("restaurantId", dish.getRestaurant().getId().toString());
                }
                payload.put("imageUrl", dish.getImage());
            }

            EntitySyncEvent event = EntitySyncEvent.builder()
                    .eventId(UUID.randomUUID())
                    .occurredAt(LocalDateTime.now())
                    .entityType("DISH")
                    .action(action)
                    .entityId(dish.getId().toString())
                    .payload(payload)
                    .build();

            store(event, "SEARCH_DISH_" + action);
            log.info("Stored dish search sync event: action={}, id={}", action, dish.getId());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot store dish search sync event", e);
        }
    }

    private void store(EntitySyncEvent payload, String eventType) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        RestaurantOutboxEvent outbox = new RestaurantOutboxEvent();
        outbox.setEventId(payload.getEventId());
        outbox.setAggregateId(payload.getEntityType() + ":" + payload.getEntityId());
        outbox.setEventType(eventType);
        outbox.setTopic(TOPIC);
        outbox.setEventKey(payload.getEntityId());
        outbox.setPayload(objectMapper.writeValueAsString(payload));
        outbox.setStatus(RestaurantOutboxEvent.Status.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outboxRepository.save(outbox);
    }
}
