package com.delivery.shipper_service.service;

import com.delivery.shipper_service.dto.event.EntitySyncEvent;
import com.delivery.shipper_service.entity.Shipper;
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
    private static final String TOPIC = "entity-sync";

    public void publishShipperChange(Shipper shipper, String action) {
        try {
            Map<String, Object> payload = new HashMap<>();
            if (!"DELETE".equals(action)) {
                // Map only what search service needs
                payload.put("id", shipper.getId().toString());
                payload.put("name", "Shipper #" + shipper.getId()); // Placeholder since name is not in entity
                payload.put("vehicleType", shipper.getVehicleType());
                payload.put("licensePlate", shipper.getLicensePlate());
                payload.put("rating", shipper.getRating());
                payload.put("isOnline", shipper.getIsOnline());
            }

            EntitySyncEvent event = EntitySyncEvent.builder()
                    .entityType("SHIPPER")
                    .action(action)
                    .entityId(shipper.getId().toString())
                    .payload(payload)
                    .build();

            kafkaTemplate.send(TOPIC, event.getEntityId(), event);
            log.info("Published shipper sync event: action={}, id={}", action, shipper.getId());
        } catch (Exception e) {
            log.error("Failed to publish shipper sync event", e);
        }
    }
}
