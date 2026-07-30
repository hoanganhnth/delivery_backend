package com.delivery.tracking_service.listener;

import com.delivery.tracking_service.dto.event.ShipperLocationUpdatedEvent;
import com.delivery.tracking_service.service.LocationHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class LocationHistoryEventListener {

    private final ObjectMapper objectMapper;
    private final LocationHistoryService history;

    public LocationHistoryEventListener(ObjectMapper objectMapper, LocationHistoryService history) {
        this.objectMapper = objectMapper;
        this.history = history;
    }

    @KafkaListener(topics = "shipper.location-updated", groupId = "tracking-location-history")
    public void handle(String payload, Acknowledgment acknowledgment) {
        try {
            ShipperLocationUpdatedEvent event = objectMapper.readValue(
                    payload, ShipperLocationUpdatedEvent.class);
            // Rolling compatibility: old producers did not carry eventId. A
            // deterministic receipt identity prevents poison/replay loops; old
            // events also have no deliveryId and are intentionally not attributed.
            if (event.getEventId() == null) {
                event.setEventId(UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8)));
            }
            history.record(event);
            acknowledgment.acknowledge();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot persist shipper location history", exception);
        }
    }
}
