package com.delivery.order_service.listener;

import com.delivery.order_service.dto.event.DeliveryStatusUpdatedEvent;
import com.delivery.order_service.dto.event.ShipperEvent;
import com.delivery.order_service.dto.event.ShipperNotFoundEvent;
import com.delivery.order_service.service.OrderEventService;
import com.delivery.order_service.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.retry.annotation.Backoff;

/**
 * ✅ Saga Command Listener — Nhận lệnh cập nhật order status từ Saga
 * Orchestrator
 *
 * TRƯỚC: Order-service tự nghe từ delivery-service, match-service
 * SAU: Chỉ nhận lệnh saga.command.update-order-status từ Saga
 *
 * Saga gửi format:
 * { "orderId": 123, "sagaStatus":
 * "SHIPPER_FOUND|SHIPPER_ASSIGNED|PICKED_UP|...", "originalEvent": "{...}" }
 */
@Slf4j
@Component
@RetryableTopic(
        attempts = "${app.kafka.retry.attempts:4}",
        backoff = @Backoff(delayExpression = "${app.kafka.retry.initial-delay-ms:1000}",
                multiplierExpression = "${app.kafka.retry.multiplier:2.0}",
                maxDelayExpression = "${app.kafka.retry.max-delay-ms:10000}"),
        exclude = IllegalArgumentException.class,
        kafkaTemplate = "retryKafkaTemplate",
        autoCreateTopics = "${app.kafka.retry.auto-create-topics:false}",
        dltTopicSuffix = ".DLT")
public class SagaCommandListener {

    private final OrderEventService orderEventService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public SagaCommandListener(OrderEventService orderEventService, OrderService orderService) {
        this.orderEventService = orderEventService;
        this.orderService = orderService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @KafkaListener(topics = "${app.kafka.input-topics.saga-update-order-status:saga.command.update-order-status}")
    public void handleUpdateOrderStatusCommand(JsonNode json, Acknowledgment acknowledgment) {
        try {
            if (json == null || !json.isObject()) {
                throw new IllegalArgumentException("Saga command must be a JSON object");
            }
            Long orderId = json.has("orderId") ? json.get("orderId").asLong() : null;
            String eventId = json.hasNonNull("eventId") ? json.get("eventId").asText() : null;
            String sagaStatus = json.has("sagaStatus") ? json.get("sagaStatus").asText() : "";
            String originalEvent = json.has("originalEvent") ? json.get("originalEvent").asText() : "{}";

            log.info("📥 [Order] Saga command: update-order-status — orderId={}, sagaStatus={}",
                    orderId, sagaStatus);

            if (orderId == null || orderId <= 0 || eventId == null) {
                throw new IllegalArgumentException(
                        "Invalid command: stable eventId and positive orderId are required");
            }
            java.util.UUID.fromString(eventId);
            JsonNode originalJson = parseOriginalEventTree(originalEvent);
            requireMatchingOrderIdentity(originalJson, orderId);

            // Delegate thẳng vào service dựa trên sagaStatus
            switch (sagaStatus) {
                // ===== Delivery status updates =====
                case "FINDING_SHIPPER", "WAIT_SHIPPER_CONFIRM",
                        "PICKED_UP", "DELIVERING", "DELIVERED", "CANCELLED" -> {
                    DeliveryStatusUpdatedEvent deliveryEvent = deliveryStatusEvent(
                            originalJson, orderId, sagaStatus);
                    orderEventService.handleDeliveryStatusUpdate(deliveryEvent);
                }

                // ===== Shipper events =====
                case "SHIPPER_ASSIGNED" -> {
                    ShipperEvent shipperEvent = parseOriginalEvent(originalEvent, ShipperEvent.class);
                    if (shipperEvent != null) {
                        shipperEvent.setOrderId(orderId);
                        orderEventService.handleShipperAccepted(shipperEvent);
                    }
                }

                case "SHIPPER_FOUND" -> {
                    DeliveryStatusUpdatedEvent deliveryEvent = deliveryStatusEvent(
                            originalJson, orderId, "WAIT_SHIPPER_CONFIRM");
                    orderEventService.handleDeliveryStatusUpdate(deliveryEvent);
                }

                case "SHIPPER_NOT_FOUND" -> {
                    ShipperNotFoundEvent notFoundEvent = parseOriginalEvent(
                            originalEvent, ShipperNotFoundEvent.class);
                    if (notFoundEvent.getDeliveryId() == null || notFoundEvent.getDeliveryId() <= 0) {
                        throw new IllegalArgumentException(
                                "SHIPPER_NOT_FOUND command requires a positive deliveryId");
                    }
                    notFoundEvent.setOrderId(orderId);
                    orderService.updateOrderStatusFromShipperNotFoundEvent(notFoundEvent);
                }

                default -> throw new IllegalArgumentException(
                        "Unknown sagaStatus: " + sagaStatus + " for orderId=" + orderId);
            }

            log.info("✅ [Order] Processed saga command for orderId={}, sagaStatus={}", orderId, sagaStatus);
            acknowledgment.acknowledge();

        } catch (IllegalArgumentException poison) {
            log.warn("Rejecting poison saga command: {}", poison.getMessage());
            throw poison;
        } catch (Exception e) {
            log.error("💥 [Order] Error processing saga command: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to process saga order command", e);
        }
    }

    private <T> T parseOriginalEvent(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("⚠️ [Order] Could not parse originalEvent into {}: {}", clazz.getSimpleName(), e.getMessage());
            throw new IllegalArgumentException(
                    "Could not parse originalEvent into " + clazz.getSimpleName(), e);
        }
    }

    private JsonNode parseOriginalEventTree(String json) {
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (parsed == null || !parsed.isObject()) {
                throw new IllegalArgumentException("originalEvent must be a JSON object");
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse originalEvent JSON", e);
        }
    }

    private DeliveryStatusUpdatedEvent deliveryStatusEvent(
            JsonNode originalEvent, Long orderId, String status) {
        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent();
        event.setOrderId(orderId);
        event.setDeliveryId(optionalPositiveLong(originalEvent, "deliveryId"));
        event.setShipperId(optionalPositiveLong(originalEvent, "shipperId"));
        event.setNotes(optionalText(originalEvent, "notes"));
        event.setStatus(status);
        return event;
    }

    private Long optionalPositiveLong(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer when present");
        }
        return value.longValue();
    }

    private String optionalText(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " must be text when present");
        }
        return value.textValue();
    }

    private void requireMatchingOrderIdentity(JsonNode originalEvent, Long commandOrderId) {
        if (!originalEvent.has("orderId")) {
            return;
        }
        JsonNode originalOrderId = originalEvent.get("orderId");
        if (originalOrderId == null || !originalOrderId.isIntegralNumber()
                || !originalOrderId.canConvertToLong()
                || originalOrderId.longValue() <= 0
                || !commandOrderId.equals(originalOrderId.longValue())) {
            throw new IllegalArgumentException(
                    "originalEvent orderId does not match saga command orderId");
        }
    }
}
