package com.delivery.tracking_service.listener;

import com.delivery.tracking_service.websocket.DeliveryRoomRegistry;
import com.delivery.tracking_service.repository.ShipperDeliveryAssignmentStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Keeps socket routing aligned with durable Delivery assignment events. */
@Component
public class ShipperDeliveryRoomListener {

    private final ObjectMapper objectMapper;
    private final DeliveryRoomRegistry rooms;
    private final ShipperDeliveryAssignmentStore assignments;

    public ShipperDeliveryRoomListener(ObjectMapper objectMapper, DeliveryRoomRegistry rooms,
                                       ShipperDeliveryAssignmentStore assignments) {
        this.objectMapper = objectMapper;
        this.rooms = rooms;
        this.assignments = assignments;
    }

    @KafkaListener(topics = "${app.kafka.topics.shipper-status-change:shipper.status-change}",
            groupId = "${app.kafka.groups.delivery-rooms:tracking-delivery-rooms}",
            containerFactory = "deliveryRoomsKafkaListenerContainerFactory")
    @SuppressWarnings("unchecked")
    public void handle(String payload, Acknowledgment acknowledgment) {
        try {
            Map<String, Object> event = objectMapper.readValue(payload, Map.class);
            long shipperId = positiveLong(event, "shipperId");
            long deliveryId = positiveLong(event, "deliveryId");
            positiveLong(event, "orderId");
            positiveLong(event, "timestamp");
            UUID.fromString(requiredString(event, "eventId"));
            String status = requiredString(event, "status").toUpperCase(Locale.ROOT);
            if (!Set.of("BUSY", "AVAILABLE").contains(status)) {
                throw new IllegalArgumentException("Unsupported shipper status");
            }
            String eventId = requiredString(event, "eventId");
            if ("BUSY".equals(status)) {
                assignments.busy(shipperId, deliveryId, positiveLong(event, "timestamp"), eventId);
                rooms.activate(deliveryId, shipperId);
            } else {
                assignments.available(shipperId, deliveryId, positiveLong(event, "timestamp"));
                rooms.end(deliveryId, shipperId);
            }
            acknowledgment.acknowledge();
        } catch (IllegalArgumentException poison) {
            throw poison;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot update delivery WebSocket room", exception);
        }
    }

    private long positiveLong(Map<String, Object> event, String field) {
        Object value = event.get(field);
        if (!(value instanceof Number number) || number.longValue() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return number.longValue();
    }

    private String requiredString(Map<String, Object> event, String field) {
        Object value = event.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return text;
    }
}
