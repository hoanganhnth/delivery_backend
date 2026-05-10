package com.delivery.search_service.consumer;

import com.delivery.search_service.document.DishDocument;
import com.delivery.search_service.document.RestaurantDocument;
import com.delivery.search_service.document.ShipperDocument;
import com.delivery.search_service.dto.EntitySyncEvent;
import com.delivery.search_service.repository.DishSearchRepository;
import com.delivery.search_service.repository.RestaurantSearchRepository;
import com.delivery.search_service.repository.ShipperSearchRepository;
import com.delivery.search_service.service.SearchCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchSyncConsumer {

    private final ObjectProvider<RestaurantSearchRepository> restaurantRepository;
    private final ObjectProvider<DishSearchRepository> dishRepository;
    private final ObjectProvider<ShipperSearchRepository> shipperRepository;
    private final SearchCacheService searchCacheService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "entity-sync", groupId = "search-service-group")
    public void consumeEntitySyncEvent(EntitySyncEvent event) {
        log.info("Received sync event for type: {}, action: {}, id: {}", 
                event.getEntityType(), event.getAction(), event.getEntityId());

        try {
            switch (event.getEntityType().toUpperCase()) {
                case "RESTAURANT":
                    handleRestaurantSync(event);
                    searchCacheService.evictByPrefix("search:restaurant:");
                    break;
                case "DISH":
                    handleDishSync(event);
                    searchCacheService.evictByPrefix("search:dish:");
                    break;
                case "SHIPPER":
                    handleShipperSync(event);
                    searchCacheService.evictByPrefix("search:shipper:");
                    break;
                default:
                    log.warn("Unknown entity type: {}", event.getEntityType());
            }
        } catch (Exception e) {
            log.error("Error processing sync event: {}", event, e);
        }
    }

    private void handleRestaurantSync(EntitySyncEvent event) {
        if (restaurantRepository.getIfAvailable() == null) {
            log.debug("Skipping restaurant sync because Elasticsearch is disabled");
            return;
        }

        if ("DELETE".equalsIgnoreCase(event.getAction())) {
            restaurantRepository.getIfAvailable().deleteById(event.getEntityId());
            return;
        }
        
        Map<String, Object> payload = event.getPayload();
        if (payload != null) {
            RestaurantDocument doc = objectMapper.convertValue(payload, RestaurantDocument.class);
            doc.setId(event.getEntityId());
            restaurantRepository.getIfAvailable().save(doc);
        }
    }

    private void handleDishSync(EntitySyncEvent event) {
        if (dishRepository.getIfAvailable() == null) {
            log.debug("Skipping dish sync because Elasticsearch is disabled");
            return;
        }

        if ("DELETE".equalsIgnoreCase(event.getAction())) {
            dishRepository.getIfAvailable().deleteById(event.getEntityId());
            return;
        }
        
        Map<String, Object> payload = event.getPayload();
        if (payload != null) {
            DishDocument doc = objectMapper.convertValue(payload, DishDocument.class);
            doc.setId(event.getEntityId());
            dishRepository.getIfAvailable().save(doc);
        }
    }

    private void handleShipperSync(EntitySyncEvent event) {
        if (shipperRepository.getIfAvailable() == null) {
            log.debug("Skipping shipper sync because Elasticsearch is disabled");
            return;
        }

        if ("DELETE".equalsIgnoreCase(event.getAction())) {
            shipperRepository.getIfAvailable().deleteById(event.getEntityId());
            return;
        }
        
        Map<String, Object> payload = event.getPayload();
        if (payload != null) {
            ShipperDocument doc = objectMapper.convertValue(payload, ShipperDocument.class);
            doc.setId(event.getEntityId());
            shipperRepository.getIfAvailable().save(doc);
        }
    }
}
