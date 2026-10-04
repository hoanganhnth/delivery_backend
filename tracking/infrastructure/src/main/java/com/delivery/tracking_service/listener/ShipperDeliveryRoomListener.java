package com.delivery.tracking_service.listener;

import com.delivery.tracking.application.api.DeliveryRoomAssignmentUseCase;
import com.delivery.tracking.application.api.DeliveryRoomAssignmentCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Keeps socket routing aligned with durable Delivery assignment events. */
@Component
public class ShipperDeliveryRoomListener {

    private final ObjectMapper objectMapper;
    private final DeliveryRoomAssignmentUseCase assignments;
    public ShipperDeliveryRoomListener(ObjectMapper objectMapper, DeliveryRoomAssignmentUseCase assignments) {
        this.objectMapper=objectMapper; this.assignments=assignments;
    }

    @KafkaListener(topics = "${app.kafka.topics.shipper-status-change:shipper.status-change}",
            groupId = "${app.kafka.groups.delivery-rooms:tracking-delivery-rooms}",
            containerFactory = "deliveryRoomsKafkaListenerContainerFactory")
    @SuppressWarnings("unchecked")
    public void handle(String payload, Acknowledgment acknowledgment) {
        try {
            Map<String, Object> event = objectMapper.readValue(payload, Map.class);
            boolean batch = event.get("batchId") instanceof String batchId && !batchId.isBlank();
            assignments.apply(new DeliveryRoomAssignmentCommand(positiveLong(event,"shipperId"),
                    positiveLong(event,"deliveryId"), positiveLong(event,"orderId"), positiveLong(event,"timestamp"),
                    requiredString(event,"eventId"), requiredString(event,"status"), batch));
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
