package com.delivery.restaurant.infrastructure.inventory;

import com.delivery.restaurant.application.api.InventoryOrderEventUseCase;
import com.delivery.restaurant.application.api.InventoryOrderEventCommand;
import com.delivery.restaurant.domain.inventory.InventoryOrderSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Decodes Kafka JSON and retry topic identity for the inventory event core. */
@Service
@ConditionalOnProperty(name = "app.restaurant.inventory-enabled", havingValue = "true")
public class JsonInventoryOrderEventAdapter {

    private final InventoryOrderEventUseCase events;
    private final ObjectMapper objectMapper;
    private final String orderCreatedTopic;
    private final String orderCancelledTopic;
    private final String refundEligibleTopic;

    public JsonInventoryOrderEventAdapter(
            InventoryOrderEventUseCase events,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.order-created:order.created}") String orderCreatedTopic,
            @Value("${app.kafka.topics.order-cancelled:order.cancelled}") String orderCancelledTopic,
            @Value("${app.kafka.topics.refund-eligible:order.refund-eligible}") String refundEligibleTopic) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.orderCreatedTopic = orderCreatedTopic;
        this.orderCancelledTopic = orderCancelledTopic;
        this.refundEligibleTopic = refundEligibleTopic;
    }

    public void process(String payload, String receivedTopic) throws Exception {
        JsonNode event = objectMapper.readTree(payload);
        UUID eventId = requiredUuid(event, "eventId");
        long orderId = requiredPositiveLong(event, "orderId");
        String sourceTopic = canonicalSourceTopic(receivedTopic);
        InventoryOrderSource source = sourceFor(sourceTopic);
        UUID reservationId = optionalUuid(event, "inventoryReservationId");
        String fingerprint = fingerprint(payload);

        events.consume(new InventoryOrderEventCommand(eventId, orderId, reservationId, sourceTopic, source, fingerprint));
    }

    private String canonicalSourceTopic(String receivedTopic) {
        if (receivedTopic == null || receivedTopic.isBlank()) {
            throw new IllegalArgumentException("source topic is required");
        }
        return receivedTopic.replaceFirst("-retry-inventory-\\d+$", "");
    }

    private InventoryOrderSource sourceFor(String sourceTopic) {
        if (orderCreatedTopic.equals(sourceTopic)) return InventoryOrderSource.ORDER_CREATED;
        if (orderCancelledTopic.equals(sourceTopic)) return InventoryOrderSource.ORDER_CANCELLED;
        if (refundEligibleTopic.equals(sourceTopic)) return InventoryOrderSource.REFUND_ELIGIBLE;
        throw new IllegalArgumentException("Unexpected inventory reservation source topic: " + sourceTopic);
    }

    private UUID requiredUuid(JsonNode event, String field) {
        JsonNode value = event == null ? null : event.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return parseUuid(value.asText(), field);
    }

    private UUID optionalUuid(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || value.isNull()) return null;
        if (value.asText().isBlank()) throw new IllegalArgumentException(field + " must be a UUID when present");
        return parseUuid(value.asText(), field);
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(field + " must be a UUID", invalid);
        }
    }

    private long requiredPositiveLong(JsonNode event, String field) {
        JsonNode value = event == null ? null : event.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value.asLong();
    }

    private String fingerprint(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

}
