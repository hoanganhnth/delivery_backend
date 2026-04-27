package com.delivery.saga_orchestrator_service.listener;

import com.delivery.saga_orchestrator_service.service.SagaManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * ✅ Kafka Event Listener — Nhận events từ các service, delegate cho SagaManager
 * phát lệnh
 *
 * Topics nhận:
 * - order.created, order.cancelled (từ order-service)
 * - delivery.created.result (từ delivery-service, sau khi nhận lệnh saga)
 * - delivery.shipper-accepted (từ delivery-service, shipper bấm accept)
 * - delivery.status-updated (từ delivery-service, thay đổi status)
 * - shipper.found, shipper.not-found (từ match-service)
 */
@Slf4j
@Component
public class KafkaEventListener {

    private final SagaManager sagaManager;
    private final ObjectMapper objectMapper;

    public KafkaEventListener(SagaManager sagaManager) {
        this.sagaManager = sagaManager;
        this.objectMapper = new ObjectMapper();
    }

    // ==================== ORDER EVENTS ====================

    @KafkaListener(topics = "order.created", groupId = "saga-orchestrator")
    public void handleOrderCreated(String message, Acknowledgment ack) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = extractLong(json, "orderId");
            log.info("📥 [Saga] order.created — orderId={}", orderId);

            sagaManager.handleOrderCreated(orderId, message);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("💥 [Saga] Error processing order.created: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "order.cancelled", groupId = "saga-orchestrator")
    public void handleOrderCancelled(String message, Acknowledgment ack) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = extractLong(json, "orderId");
            log.info("📥 [Saga] order.cancelled — orderId={}", orderId);

            sagaManager.handleOrderCancelled(orderId, message);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("💥 [Saga] Error processing order.cancelled: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    // ==================== DELIVERY RESULT EVENTS ====================

    @KafkaListener(topics = "delivery.created.result", groupId = "saga-orchestrator")
    public void handleDeliveryCreated(String message, Acknowledgment ack) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = extractLong(json, "orderId");
            Long deliveryId = extractLong(json, "deliveryId");
            log.info("📥 [Saga] delivery.created.result — orderId={}, deliveryId={}", orderId, deliveryId);

            sagaManager.handleDeliveryCreated(orderId, deliveryId, message);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("💥 [Saga] Error processing delivery.created.result: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "delivery.shipper-accepted", groupId = "saga-orchestrator")
    public void handleShipperAccepted(String message, Acknowledgment ack) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = extractLong(json, "orderId");
            Long deliveryId = extractLong(json, "deliveryId");
            Long shipperId = extractLong(json, "shipperId");
            log.info("📥 [Saga] delivery.shipper-accepted — orderId={}, shipperId={}", orderId, shipperId);

            sagaManager.handleShipperAccepted(orderId, deliveryId, shipperId, message);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("💥 [Saga] Error processing delivery.shipper-accepted: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "delivery.status-updated", groupId = "saga-orchestrator")
    public void handleDeliveryStatusUpdated(String message, Acknowledgment ack) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = extractLong(json, "orderId");
            Long deliveryId = extractLong(json, "deliveryId");
            String newStatus = "";
            if (json.has("newStatus") && !json.get("newStatus").isNull()) {
                newStatus = json.get("newStatus").asText();
            } else if (json.has("status") && !json.get("status").isNull()) {
                newStatus = json.get("status").asText();
            }
            log.info("📥 [Saga] delivery.status-updated — orderId={}, status={}", orderId, newStatus);

            sagaManager.handleDeliveryStatusUpdated(orderId, deliveryId, newStatus, message);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("💥 [Saga] Error processing delivery.status-updated: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    // ==================== MATCH RESULT EVENTS ====================

    @KafkaListener(topics = "shipper.found", groupId = "saga-orchestrator")
    public void handleShipperFound(String message, Acknowledgment ack) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = extractLong(json, "orderId");
            Long deliveryId = extractLong(json, "deliveryId");
            log.info("📥 [Saga] shipper.found — orderId={}, deliveryId={}", orderId, deliveryId);

            sagaManager.handleShipperFound(orderId, deliveryId, message);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("💥 [Saga] Error processing shipper.found: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "shipper.not-found", groupId = "saga-orchestrator")
    public void handleShipperNotFound(String message, Acknowledgment ack) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = extractLong(json, "orderId");
            Long deliveryId = extractLong(json, "deliveryId");
            log.info("📥 [Saga] shipper.not-found — orderId={}, deliveryId={}", orderId, deliveryId);

            sagaManager.handleShipperNotFound(orderId, deliveryId, message);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("💥 [Saga] Error processing shipper.not-found: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    // ==================== FAILURE EVENTS ====================

    @KafkaListener(topics = "delivery.created.failed", groupId = "saga-orchestrator")
    public void handleDeliveryCreationFailed(String message, Acknowledgment ack) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = extractLong(json, "orderId");
            String reason = json.has("reason") ? json.get("reason").asText() : "Unknown error";
            log.error("📥 [Saga] delivery.created.failed — orderId={}, reason={}", orderId, reason);

            sagaManager.handleDeliveryCreationFailed(orderId, reason, message);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("💥 [Saga] Error processing delivery.created.failed: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "delivery.cancel.failed", groupId = "saga-orchestrator")
    public void handleDeliveryCancelFailed(String message, Acknowledgment ack) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = extractLong(json, "orderId");
            String reason = json.has("reason") ? json.get("reason").asText() : "Unknown error";
            log.error("📥 [Saga] delivery.cancel.failed — orderId={}, reason={}", orderId, reason);

            sagaManager.handleStepFailed("DELIVERY_CANCEL", orderId, reason, message);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("💥 [Saga] Error processing delivery.cancel.failed: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    // ==================== HELPER ====================

    private Long extractLong(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return (node != null && !node.isNull()) ? node.asLong() : null;
    }
}
