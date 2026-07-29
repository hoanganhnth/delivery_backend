package com.delivery.delivery_service.listener;

import com.delivery.delivery_service.dto.event.OrderCreatedEvent;
import com.delivery.delivery_service.dto.event.OrderCancelledEvent;
import com.delivery.delivery_service.dto.event.ShipperFoundEvent;
import com.delivery.delivery_service.dto.event.ShipperNotFoundEvent;
import com.delivery.delivery_service.dto.event.ExpireShipperOfferCommand;
import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.service.DeliveryService;
import com.delivery.delivery_service.service.EventValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
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
    private final com.delivery.delivery_service.service.OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public OrderEventListener(DeliveryService deliveryService,
                             EventValidationService eventValidationService,
                             com.delivery.delivery_service.service.OutboxService outboxService) {
        this.deliveryService = deliveryService;
        this.eventValidationService = eventValidationService;
        this.outboxService = outboxService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * ✅ Nhận lệnh từ Saga: Tạo delivery record
     * Sau khi tạo xong → publish delivery.created.result cho Saga
     */
    @KafkaListener(topics = "${app.kafka.topics.create-delivery:saga.command.create-delivery}")
    public void handleCreateDeliveryCommand(
            String message,
            Acknowledgment acknowledgment) {
        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(message, OrderCreatedEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse create-delivery command", e);
        }
        log.info("📥 [Delivery] Saga command: create-delivery for orderId={}", event.getOrderId());

        if (event.getEventId() == null || event.getOrderId() == null || event.getOrderId() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid create command: stable eventId and positive orderId are required");
        }
        EventValidationService.ValidationResult validationResult =
                eventValidationService.validateOrderCreatedEvent(event);
        if (!validationResult.isValid()) {
            log.error("🚫 [Delivery] Invalid event for orderId={}, reporting correlated failure",
                    event.getOrderId());
            publishFailure("delivery.created.failed", event.getEventId(), event.getOrderId(),
                    validationResult.getErrorMessage());
            acknowledgment.acknowledge();
            return;
        }

        try {
            DeliveryResponse response = deliveryService.createDeliveryFromOrderEvent(event);
            log.info("✅ [Delivery] Created delivery record for orderId={}, deliveryId={}",
                    event.getOrderId(), response.getId());
        } catch (Exception e) {
            log.error("💥 [Delivery] Error creating delivery for orderId={}: {}",
                    event.getOrderId(), e.getMessage(), e);
            publishFailure("delivery.created.failed", event.getEventId(), event.getOrderId(), e.getMessage());
        }
        // ACK is deliberately outside the processing catch. An ACK failure after
        // a committed success must trigger redelivery, never a false Saga failure.
        acknowledgment.acknowledge();
    }

    /**
     * ✅ Nhận lệnh từ Saga: Huỷ delivery
     */
    @KafkaListener(topics = "${app.kafka.topics.cancel-delivery:saga.command.cancel-delivery}")
    public void handleCancelDeliveryCommand(
            String message,
            Acknowledgment acknowledgment) {

        OrderCancelledEvent event;
        try {
            event = objectMapper.readValue(message, OrderCancelledEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse cancel-delivery command", e);
        }
        log.info("📥 [Delivery] Saga command: cancel-delivery for orderId={}", event.getOrderId());

        if (event.getEventId() == null || event.getOrderId() == null || event.getOrderId() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid cancel command: stable eventId and positive orderId are required");
        }
        try {
            deliveryService.cancelDeliveryFromOrderCancelledEvent(event);
            log.info("✅ [Delivery] Cancelled delivery for orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("💥 [Delivery] Error cancelling delivery for orderId={}: {}",
                    event.getOrderId(), e.getMessage(), e);
            publishFailure("delivery.cancel.failed", event.getEventId(), event.getOrderId(), e.getMessage());
        }
        // Keep ACK failures outside the business-failure mapping for the same
        // reason as create-delivery above.
        acknowledgment.acknowledge();
    }

    /** Persist the selected offer before notification-service informs the shipper. */
    @KafkaListener(topics = "${app.kafka.topics.cache-shipper-found:saga.command.cache-shipper-found}")
    public void handleCacheShipperOfferCommand(String message, Acknowledgment acknowledgment) throws Exception {
        ShipperFoundEvent event = objectMapper.readValue(message, ShipperFoundEvent.class);
        log.info("📥 [Delivery] Saga command: cache-shipper-found for orderId={}", event.getOrderId());
        deliveryService.cacheShipperOffer(event);
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = "${app.kafka.topics.expire-shipper-offer:saga.command.expire-shipper-offer}")
    public void handleExpireShipperOfferCommand(String message, Acknowledgment acknowledgment) throws Exception {
        ExpireShipperOfferCommand command = objectMapper.readValue(message, ExpireShipperOfferCommand.class);
        deliveryService.expireShipperOffer(command);
        acknowledgment.acknowledge();
    }

    /** Apply the terminal no-shipper result without misclassifying it as cancellation. */
    @KafkaListener(topics = "${app.kafka.topics.mark-shipper-not-found:saga.command.mark-shipper-not-found}")
    public void handleMarkShipperNotFoundCommand(String message, Acknowledgment acknowledgment) throws Exception {
        ShipperNotFoundEvent event = objectMapper.readValue(message, ShipperNotFoundEvent.class);
        if (event.getEventId() == null
                || event.getOrderId() == null || event.getOrderId() <= 0
                || event.getDeliveryId() == null || event.getDeliveryId() <= 0) {
            throw new IllegalArgumentException(
                    "Shipper-not-found command requires eventId and positive order/delivery IDs");
        }
        deliveryService.updateDeliveryStatusFromShipperNotFoundEvent(event);
        acknowledgment.acknowledge();
    }



    // ==================== HELPER ====================

    /**
     * ✅ Gửi thông báo lỗi cho Saga để nó kích hoạt compensation
     */
    private void publishFailure(String topic, java.util.UUID commandEventId, Long orderId, String reason) {
            Map<String, Object> failure = new HashMap<>();
            failure.put("commandEventId", commandEventId.toString());
            failure.put("orderId", orderId);
            failure.put("success", false);
            failure.put("reason", reason);
            outboxService.saveEvent(commandEventId, "ORDER", orderId.toString(), "DELIVERY_COMMAND_FAILED",
                    topic, orderId.toString(), failure);
            log.warn("🚨 [Delivery] Stored failure in outbox for {} orderId={}: {}", topic, orderId, reason);
    }
}
