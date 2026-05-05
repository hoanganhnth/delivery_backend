package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.event.EntitySyncEvent;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchSyncPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String TOPIC = "entity-sync";

    public void publishRestaurantChange(Restaurant restaurant, String action) {
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
                    .entityType("RESTAURANT")
                    .action(action)
                    .entityId(restaurant.getId().toString())
                    .payload(payload)
                    .build();

            kafkaTemplate.send(TOPIC, event.getEntityId(), event);
            log.info("Published restaurant sync event: action={}, id={}", action, restaurant.getId());
        } catch (Exception e) {
            log.error("Failed to publish restaurant sync event", e);
        }
    }

    public void publishDishChange(MenuItem dish, String action) {
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
                    .entityType("DISH")
                    .action(action)
                    .entityId(dish.getId().toString())
                    .payload(payload)
                    .build();

            kafkaTemplate.send(TOPIC, event.getEntityId(), event);
            log.info("Published dish sync event: action={}, id={}", action, dish.getId());
        } catch (Exception e) {
            log.error("Failed to publish dish sync event", e);
        }
    }
}
