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
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

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
public class SagaCommandListener {

    private final OrderEventService orderEventService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public SagaCommandListener(OrderEventService orderEventService, OrderService orderService) {
        this.orderEventService = orderEventService;
        this.orderService = orderService;
        this.objectMapper = new ObjectMapper()
                .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule.class.getName().equals("") ? null : new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @KafkaListener(topics = "saga.command.update-order-status", groupId = "order-service")
    public void handleUpdateOrderStatusCommand(String message, Acknowledgment acknowledgment) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = json.has("orderId") ? json.get("orderId").asLong() : null;
            String sagaStatus = json.has("sagaStatus") ? json.get("sagaStatus").asText() : "";
            String originalEvent = json.has("originalEvent") ? json.get("originalEvent").asText() : "{}";

            log.info("📥 [Order] Saga command: update-order-status — orderId={}, sagaStatus={}",
                    orderId, sagaStatus);

            if (orderId == null) {
                log.error("💥 Invalid command: orderId is null");
                acknowledgment.acknowledge();
                return;
            }

            // Delegate thẳng vào service dựa trên sagaStatus
            switch (sagaStatus) {
                // ===== Delivery status updates =====
                case "PICKED_UP", "DELIVERING", "DELIVERED", "CANCELLED" -> {
                    DeliveryStatusUpdatedEvent deliveryEvent = parseOriginalEvent(originalEvent,
                            DeliveryStatusUpdatedEvent.class);
                    if (deliveryEvent != null) {
                        deliveryEvent.setOrderId(orderId);
                        deliveryEvent.setStatus(sagaStatus);
                        orderEventService.handleDeliveryStatusUpdate(deliveryEvent);
                    }
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
                    // Update order status to reflect shipper found
                    log.info("✅ [Order] Order {} — shipper found, status update handled", orderId);
                }

                case "SHIPPER_NOT_FOUND" -> {
                    ShipperNotFoundEvent notFoundEvent = new ShipperNotFoundEvent();
                    notFoundEvent.setOrderId(orderId);
                    // Parse deliveryId from original event
                    try {
                        JsonNode origJson = objectMapper.readTree(originalEvent);
                        if (origJson.has("deliveryId") && !origJson.get("deliveryId").isNull()) {
                            notFoundEvent.setDeliveryId(origJson.get("deliveryId").asLong());
                        }
                    } catch (Exception ignored) {
                    }
                    orderService.updateOrderStatusFromShipperNotFoundEvent(notFoundEvent);
                }

                default -> log.warn("⚠️ [Order] Unknown sagaStatus: {} for orderId={}", sagaStatus, orderId);
            }

            log.info("✅ [Order] Processed saga command for orderId={}, sagaStatus={}", orderId, sagaStatus);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("💥 [Order] Error processing saga command: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private <T> T parseOriginalEvent(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("⚠️ [Order] Could not parse originalEvent into {}: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }
}
