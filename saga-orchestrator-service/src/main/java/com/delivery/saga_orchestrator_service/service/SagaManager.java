package com.delivery.saga_orchestrator_service.service;

import com.delivery.saga_orchestrator_service.entity.SagaInstance;
import com.delivery.saga_orchestrator_service.entity.SagaInstance.SagaStatus;
import com.delivery.saga_orchestrator_service.entity.SagaStep;
import com.delivery.saga_orchestrator_service.repository.SagaInstanceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * ✅ Active Saga Manager — "Nhạc trưởng" phát lệnh điều phối luồng đặt hàng
 *
 * Flow:
 * 1. order.created → SAGA → [saga.command.create-delivery] → Delivery
 * 2. delivery.created.result → SAGA → [saga.command.find-shipper] → Match
 * 3. shipper.found → SAGA → [saga.command.cache-shipper-found] → Delivery
 *                          → [saga.command.update-order-status] → Order
 * 4. shipper.not-found → SAGA → [saga.command.update-order-status](FAILED) → Order
 *                              → [saga.command.cancel-delivery] → Delivery
 * 5. delivery.shipper-accepted → SAGA → [saga.command.update-order-status] → Order
 * 6. delivery.status-updated → SAGA → [saga.command.update-order-status] → Order
 */
@Slf4j
@Service
public class SagaManager {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // ======== Saga Command Topics ========
    public static final String CMD_CREATE_DELIVERY = "saga.command.create-delivery";
    public static final String CMD_CANCEL_DELIVERY = "saga.command.cancel-delivery";
    public static final String CMD_FIND_SHIPPER = "saga.command.find-shipper";
    public static final String CMD_CACHE_SHIPPER_FOUND = "saga.command.cache-shipper-found";
    public static final String CMD_STOP_MATCHING = "saga.command.stop-matching";
    public static final String CMD_UPDATE_ORDER_STATUS = "saga.command.update-order-status";

    public SagaManager(SagaInstanceRepository sagaInstanceRepository,
                       KafkaTemplate<String, Object> kafkaTemplate) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
    }

    // ==================== EVENT HANDLERS ====================

    /**
     * Step 1: order.created → Tạo saga + phát lệnh tạo delivery
     */
    @Transactional
    public void handleOrderCreated(Long orderId, String rawEvent) {
        // Idempotent check
        if (sagaInstanceRepository.findByOrderId(orderId).isPresent()) {
            log.warn("⚠️ [Saga] Duplicate order.created for orderId={}, skipping", orderId);
            return;
        }

        SagaInstance saga = new SagaInstance();
        saga.setSagaType("ORDER_CREATION");
        saga.setOrderId(orderId);
        saga.setStatus(SagaStatus.STARTED);
        saga.setPayload(rawEvent);
        saga.addStep("ORDER_CREATED", "order.created", rawEvent);
        sagaInstanceRepository.save(saga);

        log.info("🆕 [Saga] Created saga for orderId={}, id={}", orderId, saga.getId());

        // ✅ PHÁT LỆNH: Tạo delivery
        sendCommand(CMD_CREATE_DELIVERY, orderId.toString(), rawEvent);
        log.info("📤 [Saga] Sent command: {} for orderId={}", CMD_CREATE_DELIVERY, orderId);
    }

    /**
     * Step 2: delivery.created.result → Phát lệnh tìm shipper
     */
    @Transactional
    public void handleDeliveryCreated(Long orderId, Long deliveryId, String rawEvent) {
        SagaInstance saga = findSagaByOrderId(orderId);
        if (saga == null) return;

        saga.setDeliveryId(deliveryId);
        saga.setStatus(SagaStatus.DELIVERY_CREATED);
        saga.addStep("DELIVERY_CREATED", "delivery.created.result", rawEvent);
        sagaInstanceRepository.save(saga);

        try {
            // ✅ Inject retry/timeout configuration for match-service
            ObjectNode payloadNode = (ObjectNode) objectMapper.readTree(rawEvent);
            payloadNode.put("maxRetryAttempts", 10);
            payloadNode.put("initialDelaySeconds", 30);
            payloadNode.put("maxDelaySeconds", 300);
            payloadNode.put("backoffMultiplier", 1.5);
            
            String modifiedEvent = objectMapper.writeValueAsString(payloadNode);
            
            // ✅ PHÁT LỆNH: Tìm shipper
            sendCommand(CMD_FIND_SHIPPER, orderId.toString(), modifiedEvent);
            log.info("📤 [Saga] Sent command: {} for orderId={}, deliveryId={} with retry settings", CMD_FIND_SHIPPER, orderId, deliveryId);
        } catch (Exception e) {
            log.error("💥 [Saga] Failed to inject retry settings into find-shipper command", e);
            // Fallback to original event if parsing fails
            sendCommand(CMD_FIND_SHIPPER, orderId.toString(), rawEvent);
            log.info("📤 [Saga] Sent command: {} for orderId={}, deliveryId={} without retry settings", CMD_FIND_SHIPPER, orderId, deliveryId);
        }

        // Update saga status
        saga.setStatus(SagaStatus.FINDING_SHIPPER);
        sagaInstanceRepository.save(saga);
    }

    /**
     * Step 3a: shipper.found → Phát lệnh cache shipper + update order status
     */
    @Transactional
    public void handleShipperFound(Long orderId, Long deliveryId, String rawEvent) {
        SagaInstance saga = findSagaByOrderId(orderId);
        if (saga == null) return;

        saga.setStatus(SagaStatus.SHIPPER_FOUND);
        saga.addStep("SHIPPER_FOUND", "shipper.found", rawEvent);
        sagaInstanceRepository.save(saga);

        // ✅ PHÁT LỆNH 1: Cache shipper found cho delivery-service
        sendCommand(CMD_CACHE_SHIPPER_FOUND, orderId.toString(), rawEvent);

        // ✅ PHÁT LỆNH 2: Cập nhật order status
        sendOrderStatusCommand(orderId, "SHIPPER_FOUND", rawEvent);

        log.info("📤 [Saga] Sent commands: cache-shipper + update-order for orderId={}", orderId);
    }

    /**
     * Step 3b: shipper.not-found → Compensation: cancel delivery + update order FAILED
     */
    @Transactional
    public void handleShipperNotFound(Long orderId, Long deliveryId, String rawEvent) {
        SagaInstance saga = findSagaByOrderId(orderId);
        if (saga == null) return;

        saga.setStatus(SagaStatus.FAILED);
        saga.setCompletedAt(LocalDateTime.now());
        saga.addStep("SHIPPER_NOT_FOUND", "shipper.not-found", rawEvent);
        sagaInstanceRepository.save(saga);

        // ✅ COMPENSATION: Cập nhật delivery status
        sendCommand(CMD_CANCEL_DELIVERY, orderId.toString(), rawEvent);

        // ✅ COMPENSATION: Cập nhật order status → FAILED
        sendOrderStatusCommand(orderId, "SHIPPER_NOT_FOUND", rawEvent);

        log.warn("🚨 [Saga] COMPENSATION — shipper not found, orderId={}", orderId);
    }

    /**
     * Step 4: delivery.shipper-accepted → Cập nhật order status
     */
    @Transactional
    public void handleShipperAccepted(Long orderId, Long deliveryId, Long shipperId, String rawEvent) {
        SagaInstance saga = findSagaByOrderId(orderId);
        if (saga == null) return;

        saga.setStatus(SagaStatus.SHIPPER_ASSIGNED);
        saga.setShipperId(shipperId);
        saga.addStep("SHIPPER_ASSIGNED", "delivery.shipper-accepted", rawEvent);
        sagaInstanceRepository.save(saga);

        // ✅ PHÁT LỆNH: Cập nhật order status
        sendOrderStatusCommand(orderId, "SHIPPER_ASSIGNED", rawEvent);

        log.info("📤 [Saga] Shipper {} assigned, sent update-order for orderId={}", shipperId, orderId);
    }

    /**
     * Step 4b: delivery.shipper-rejected → Re-trigger tìm shipper mới (loại trừ shipper đã reject)
     */
    @Transactional
    public void handleShipperRejected(Long orderId, Long deliveryId, Long rejectedShipperId, String rawEvent) {
        SagaInstance saga = findSagaByOrderId(orderId);
        if (saga == null) return;

        // Đếm số lần shipper đã reject cho đơn này
        long rejectCount = saga.getSteps().stream()
                .filter(s -> s.getStepName().startsWith("SHIPPER_REJECTED"))
                .count() + 1;

        // Giới hạn tối đa 5 lần re-assign
        if (rejectCount > 5) {
            log.warn("🚨 [Saga] Too many shipper rejections ({}) for orderId={}, failing saga", rejectCount, orderId);
            saga.setStatus(SagaStatus.FAILED);
            saga.setCompletedAt(LocalDateTime.now());
            saga.addStep("SHIPPER_REJECTED_LIMIT", "delivery.shipper-rejected", rawEvent);
            sagaInstanceRepository.save(saga);

            sendCommand(CMD_CANCEL_DELIVERY, orderId.toString(), rawEvent);
            sendOrderStatusCommand(orderId, "SHIPPER_NOT_FOUND", rawEvent);
            return;
        }

        saga.setStatus(SagaStatus.FINDING_SHIPPER);
        saga.addStep("SHIPPER_REJECTED_" + rejectCount, "delivery.shipper-rejected", rawEvent);
        sagaInstanceRepository.save(saga);

        log.info("🔄 [Saga] Shipper {} rejected orderId={} (attempt {}), re-triggering find-shipper",
                rejectedShipperId, orderId, rejectCount);

        // ✅ Collect all rejected shipper IDs from saga steps
        java.util.List<Long> excludedShipperIds = new java.util.ArrayList<>();
        if (rejectedShipperId != null) {
            excludedShipperIds.add(rejectedShipperId);
        }
        // Also extract from previous rejection steps
        for (SagaStep step : saga.getSteps()) {
            if (step.getStepName().startsWith("SHIPPER_REJECTED") && step.getEventData() != null) {
                try {
                    com.fasterxml.jackson.databind.JsonNode stepData = objectMapper.readTree(step.getEventData());
                    if (stepData.has("rejectedShipperId")) {
                        Long prevRejected = stepData.get("rejectedShipperId").asLong();
                        if (!excludedShipperIds.contains(prevRejected)) {
                            excludedShipperIds.add(prevRejected);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        try {
            // ✅ Enrich payload with excludedShipperIds + retry settings for match-service
            ObjectNode payloadNode = (ObjectNode) objectMapper.readTree(rawEvent);
            payloadNode.put("maxRetryAttempts", 5);
            payloadNode.put("initialDelaySeconds", 15);
            payloadNode.put("maxDelaySeconds", 120);
            payloadNode.put("backoffMultiplier", 1.5);

            // Add excluded shipper IDs
            com.fasterxml.jackson.databind.node.ArrayNode excludedArray = objectMapper.createArrayNode();
            for (Long id : excludedShipperIds) {
                excludedArray.add(id);
            }
            payloadNode.set("excludedShipperIds", excludedArray);

            String modifiedEvent = objectMapper.writeValueAsString(payloadNode);
            sendCommand(CMD_FIND_SHIPPER, orderId.toString(), modifiedEvent);

            log.info("📤 [Saga] Re-sent {} for orderId={} with excludedShippers={}", 
                    CMD_FIND_SHIPPER, orderId, excludedShipperIds);
        } catch (Exception e) {
            log.error("💥 [Saga] Failed to re-send find-shipper command for orderId={}", orderId, e);
            sendCommand(CMD_FIND_SHIPPER, orderId.toString(), rawEvent);
        }

        // ✅ Update order status to let frontend know we're re-searching
        sendOrderStatusCommand(orderId, "FINDING_SHIPPER", rawEvent);
    }

    /**
     * Step 5: delivery.status-updated → Forward status đến order-service
     */
    @Transactional
    public void handleDeliveryStatusUpdated(Long orderId, Long deliveryId, String newStatus, String rawEvent) {
        SagaInstance saga = findSagaByOrderId(orderId);
        if (saga == null) return;

        // Cập nhật saga status
        switch (newStatus) {
            case "PICKED_UP" -> saga.setStatus(SagaStatus.PICKING_UP);
            case "DELIVERING" -> saga.setStatus(SagaStatus.DELIVERING);
            case "DELIVERED" -> {
                saga.setStatus(SagaStatus.COMPLETED);
                saga.setCompletedAt(LocalDateTime.now());
            }
            case "CANCELLED" -> {
                saga.setStatus(SagaStatus.CANCELLED);
                saga.setCompletedAt(LocalDateTime.now());
            }
        }

        saga.addStep("DELIVERY_" + newStatus, "delivery.status-updated", rawEvent);
        sagaInstanceRepository.save(saga);

        // ✅ PHÁT LỆNH: Cập nhật order status
        sendOrderStatusCommand(orderId, newStatus, rawEvent);

        log.info("📤 [Saga] Delivery status={}, forwarded to order for orderId={}", newStatus, orderId);
    }

    /**
     * order.cancelled → Compensation: cancel delivery + stop matching
     */
    @Transactional
    public void handleOrderCancelled(Long orderId, String rawEvent) {
        SagaInstance saga = findSagaByOrderId(orderId);
        if (saga == null) return;

        saga.setStatus(SagaStatus.CANCELLED);
        saga.setCompletedAt(LocalDateTime.now());
        saga.addStep("ORDER_CANCELLED", "order.cancelled", rawEvent);
        sagaInstanceRepository.save(saga);

        try {
            // Enrich payload with deliveryId for Match/Delivery services
            ObjectNode payloadNode = (ObjectNode) objectMapper.readTree(rawEvent);
            if (saga.getDeliveryId() != null) {
                payloadNode.put("deliveryId", saga.getDeliveryId());
            }
            String enrichedEvent = objectMapper.writeValueAsString(payloadNode);

            // ✅ COMPENSATION: Huỷ delivery
            sendCommand(CMD_CANCEL_DELIVERY, orderId.toString(), enrichedEvent);

            // ✅ COMPENSATION: Dừng tìm shipper
            sendCommand(CMD_STOP_MATCHING, orderId.toString(), enrichedEvent);
        } catch (Exception e) {
            // Fallback
            sendCommand(CMD_CANCEL_DELIVERY, orderId.toString(), rawEvent);
            sendCommand(CMD_STOP_MATCHING, orderId.toString(), rawEvent);
        }

        log.warn("🚨 [Saga] COMPENSATION — order cancelled, orderId={}", orderId);
    }

    // ==================== FAILURE HANDLERS ====================

    /**
     * ❌ delivery.created.failed → Tạo delivery thất bại → báo Order cancel
     */
    @Transactional
    public void handleDeliveryCreationFailed(Long orderId, String reason, String rawEvent) {
        SagaInstance saga = findSagaByOrderId(orderId);
        if (saga == null) return;

        saga.setStatus(SagaStatus.COMPENSATING);
        saga.addStep("DELIVERY_CREATION_FAILED", "delivery.created.failed", rawEvent);
        sagaInstanceRepository.save(saga);

        log.error("🚨 [Saga] COMPENSATION — Delivery creation failed for orderId={}: {}", orderId, reason);

        // ✅ COMPENSATION: Báo order-service → cập nhật status thất bại
        sendOrderStatusCommand(orderId, "DELIVERY_CREATION_FAILED", rawEvent);

        // Đánh dấu saga thất bại
        saga.setStatus(SagaStatus.FAILED);
        saga.setCompletedAt(LocalDateTime.now());
        sagaInstanceRepository.save(saga);
    }

    /**
     * ❌ Xử lý generic failure từ bất kỳ step nào
     */
    @Transactional
    public void handleStepFailed(String stepName, Long orderId, String reason, String rawEvent) {
        SagaInstance saga = findSagaByOrderId(orderId);
        if (saga == null) return;

        saga.setStatus(SagaStatus.COMPENSATING);
        saga.addStep(stepName + "_FAILED", stepName + ".failed", rawEvent);

        log.error("🚨 [Saga] COMPENSATION — Step {} failed for orderId={}: {}", stepName, orderId, reason);

        // Compensation dựa trên step hiện tại
        switch (saga.getStatus()) {
            case FINDING_SHIPPER, SHIPPER_FOUND -> {
                try {
                    ObjectNode payloadNode = (ObjectNode) objectMapper.readTree(rawEvent);
                    if (saga.getDeliveryId() != null) {
                        payloadNode.put("deliveryId", saga.getDeliveryId());
                    }
                    String enrichedEvent = objectMapper.writeValueAsString(payloadNode);

                    // Đã tạo delivery rồi → huỷ nó
                    sendCommand(CMD_CANCEL_DELIVERY, orderId.toString(), enrichedEvent);
                    sendCommand(CMD_STOP_MATCHING, orderId.toString(), enrichedEvent);
                } catch (Exception e) {
                    sendCommand(CMD_CANCEL_DELIVERY, orderId.toString(), rawEvent);
                }
                sendOrderStatusCommand(orderId, "FAILED", rawEvent);
            }
            default -> {
                // Nếu chưa có gì cần dọn → chỉ báo order failed
                sendOrderStatusCommand(orderId, "FAILED", rawEvent);
            }
        }

        saga.setStatus(SagaStatus.FAILED);
        saga.setCompletedAt(LocalDateTime.now());
        sagaInstanceRepository.save(saga);
    }

    // ==================== HELPERS ====================

    private SagaInstance findSagaByOrderId(Long orderId) {
        if (orderId == null) return null;
        return sagaInstanceRepository.findByOrderId(orderId).orElseGet(() -> {
            log.warn("⚠️ [Saga] No saga found for orderId={}", orderId);
            return null;
        });
    }

    private void sendCommand(String topic, String key, String payload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            kafkaTemplate.send(topic, key, jsonNode);
        } catch (Exception e) {
            log.error("💥 [Saga] Failed to send command to {}: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * Gửi lệnh update order status (bọc thêm trường sagaStatus)
     */
    private void sendOrderStatusCommand(Long orderId, String sagaStatus, String rawEvent) {
        try {
            ObjectNode command = objectMapper.createObjectNode();
            command.put("orderId", orderId);
            command.put("sagaStatus", sagaStatus);
            command.put("originalEvent", rawEvent);
            command.put("timestamp", System.currentTimeMillis());

            kafkaTemplate.send(CMD_UPDATE_ORDER_STATUS, orderId.toString(), command);
        } catch (Exception e) {
            log.error("💥 [Saga] Failed to send order status command: {}", e.getMessage(), e);
        }
    }
}
