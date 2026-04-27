package com.delivery.delivery_service.listener;

import com.delivery.delivery_service.dto.event.OrderCreatedEvent;
import com.delivery.delivery_service.dto.event.OrderCancelledEvent;
import com.delivery.delivery_service.dto.event.ShipperFoundEvent;
import com.delivery.delivery_service.dto.event.ShipperNotFoundEvent;
import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.service.DeliveryService;
import com.delivery.delivery_service.service.DeliveryEventPublisher;
import com.delivery.delivery_service.service.EventValidationService;
import com.delivery.delivery_service.service.DeliveryService;
import com.delivery.delivery_service.service.DeliveryEventPublisher;
import com.delivery.delivery_service.service.EventValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * ✅ Saga Command Listener — Nhận lệnh từ Saga Orchestrator
 *
 * TRƯỚC: Nghe trực tiếp order.created, shipper.found, shipper.not-found
 * SAU:   Chỉ nghe saga.command.* từ Saga Orchestrator
 */
@Slf4j
@Component
public class OrderEventListener {

    private final DeliveryService deliveryService;
    private final EventValidationService eventValidationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderEventListener(DeliveryService deliveryService,
                             EventValidationService eventValidationService,
                             KafkaTemplate<String, Object> kafkaTemplate) {
        this.deliveryService = deliveryService;
        this.eventValidationService = eventValidationService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * ✅ Nhận lệnh từ Saga: Tạo delivery record
     * Sau khi tạo xong → publish delivery.created.result cho Saga
     */
    @KafkaListener(topics = "saga.command.create-delivery", groupId = "delivery-service")
    public void handleCreateDeliveryCommand(
            String message,
            Acknowledgment acknowledgment) {

        OrderCreatedEvent event = null;
        try {
            event = objectMapper.readValue(message, OrderCreatedEvent.class);
            log.info("📥 [Delivery] Saga command: create-delivery for orderId={}", event.getOrderId());

            // Validate event
            EventValidationService.ValidationResult validationResult =
                    eventValidationService.validateOrderCreatedEvent(event);

            if (!validationResult.isValid() && !eventValidationService.hasMinimumRequiredFields(event)) {
                log.error("🚫 [Delivery] Invalid event for orderId={}, skipping", event.getOrderId());
                publishFailure("delivery.created.failed", event.getOrderId(), "Invalid event data");
                acknowledgment.acknowledge();
                return;
            }

            // Tạo delivery record
            DeliveryResponse response = deliveryService.createDeliveryFromOrderEvent(event);

            log.info("✅ [Delivery] Created delivery record for orderId={}, deliveryId={}",
                    event.getOrderId(), response.getId());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            Long orderId = (event != null) ? event.getOrderId() : null;
            log.error("💥 [Delivery] Error creating delivery for orderId={}: {}",
                    orderId, e.getMessage(), e);
            // ✅ BÁO LỖI CHO SAGA để nó compensation
            publishFailure("delivery.created.failed", orderId, e.getMessage());
            acknowledgment.acknowledge();
        }
    }

    /**
     * ✅ Nhận lệnh từ Saga: Huỷ delivery
     */
    @KafkaListener(topics = "saga.command.cancel-delivery", groupId = "delivery-service")
    public void handleCancelDeliveryCommand(
            String message,
            Acknowledgment acknowledgment) {

        OrderCancelledEvent event = null;
        try {
            event = objectMapper.readValue(message, OrderCancelledEvent.class);
            log.info("📥 [Delivery] Saga command: cancel-delivery for orderId={}", event.getOrderId());

            if (event.getOrderId() == null) {
                log.error("💥 Invalid cancel command: orderId is null");
                acknowledgment.acknowledge();
                return;
            }

            deliveryService.cancelDeliveryFromOrderCancelledEvent(event);
            log.info("✅ [Delivery] Cancelled delivery for orderId={}", event.getOrderId());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            Long orderId = (event != null) ? event.getOrderId() : null;
            log.error("💥 [Delivery] Error cancelling delivery for orderId={}: {}",
                    orderId, e.getMessage(), e);
            publishFailure("delivery.cancel.failed", orderId, e.getMessage());
            acknowledgment.acknowledge();
        }
    }



    // ==================== HELPER ====================

    /**
     * ✅ Gửi thông báo lỗi cho Saga để nó kích hoạt compensation
     */
    private void publishFailure(String topic, Long orderId, String reason) {
        try {
            Map<String, Object> failure = new HashMap<>();
            failure.put("orderId", orderId);
            failure.put("success", false);
            failure.put("reason", reason);
            failure.put("timestamp", System.currentTimeMillis());
            kafkaTemplate.send(topic, orderId != null ? orderId.toString() : "unknown", failure);
            log.warn("🚨 [Delivery] Published failure to {} for orderId={}: {}", topic, orderId, reason);
        } catch (Exception e) {
            log.error("💥 [Delivery] Failed to publish failure event: {}", e.getMessage());
        }
    }
}
