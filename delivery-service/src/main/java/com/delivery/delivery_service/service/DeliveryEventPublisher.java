package com.delivery.delivery_service.service;

import com.delivery.delivery_service.common.constants.KafkaTopicConstants;
import com.delivery.delivery_service.dto.event.ShipperAcceptedEvent;
import com.delivery.delivery_service.dto.event.DeliveryCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ✅ Event Publisher — Transactional Outbox Pattern
 *
 * TRƯỚC: kafkaTemplate.send() trực tiếp → mất event nếu server sập
 * SAU:   outboxService.saveEvent() → lưu cùng DB transaction → không bao giờ mất
 *
 * OutboxMessageRelay sẽ poll bảng outbox → gửi lên Kafka
 */
@Slf4j
@Service
public class DeliveryEventPublisher {

    private final OutboxService outboxService;

    @Value("${app.kafka.topics.delivery-status-updated:delivery.status-updated}")
    private String deliveryStatusUpdatedTopic = KafkaTopicConstants.DELIVERY_STATUS_UPDATED_TOPIC;
    @Value("${app.kafka.topics.delivery-completed:delivery.completed}")
    private String deliveryCompletedTopic = KafkaTopicConstants.DELIVERY_COMPLETED_TOPIC;
    @Value("${app.kafka.topics.shipper-status-change:shipper.status-change}")
    private String shipperStatusChangeTopic = KafkaTopicConstants.SHIPPER_STATUS_CHANGE_TOPIC;

    public DeliveryEventPublisher(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    /**
     * Gửi ShipperAcceptedEvent
     */
    public void publishShipperAcceptedEvent(ShipperAcceptedEvent event) {
        log.info("📦 [Kafka] Sending ShipperAcceptedEvent for order: {}, shipper: {}",
                event.getOrderId(), event.getShipperId());
        outboxService.saveEvent("ORDER", event.getOrderId().toString(), "SHIPPER_ACCEPTED",
                KafkaTopicConstants.SHIPPER_ACCEPTED_TOPIC, event.getOrderId().toString(), event);
    }

    /**
     * Gửi delivery status update event
     */
    public void publishDeliveryStatusUpdated(Long deliveryId, Long orderId, Long userId, Long shipperId,
                                             String status, String previousStatus) {
        log.info("📦 [Kafka] Sending delivery status update: {} -> {} for delivery: {}, order: {}",
                previousStatus, status, deliveryId, orderId);

        DeliveryStatusUpdateEvent statusEvent = new DeliveryStatusUpdateEvent(
                deliveryId, orderId, userId, shipperId, status, previousStatus
        );

        save(deliveryId, "DELIVERY_STATUS_UPDATED", deliveryStatusUpdatedTopic, statusEvent);
    }

    /**
     * Inner class for delivery status update events
     */
    public static class DeliveryStatusUpdateEvent {
        public final Long deliveryId;
        public final Long orderId;
        public final Long userId;
        public final Long shipperId;
        public final String status;
        public final String newStatus;
        public final String oldStatus;
        public final String eventType = "DELIVERY_STATUS_UPDATED";
        public final LocalDateTime timestamp = LocalDateTime.now();

        public DeliveryStatusUpdateEvent(Long deliveryId, Long orderId, Long userId, Long shipperId,
                                         String newStatus, String oldStatus) {
            this.deliveryId = deliveryId;
            this.orderId = orderId;
            this.userId = userId;
            this.shipperId = shipperId;
            this.status = newStatus;
            this.newStatus = newStatus;
            this.oldStatus = oldStatus;
        }
    }

    /**
     * ✅ Publish DeliveryCompletedEvent — SỰ KIỆN QUAN TRỌNG NHẤT (liên quan đến tiền)
     */
    public void publishDeliveryCompletedEvent(DeliveryCompletedEvent event) {
        log.info("📦 [Kafka] Sending DeliveryCompletedEvent for delivery: {}, shipper: {}, amount: {}",
                event.getDeliveryId(), event.getShipperId(), event.getShippingFee());
        save(event.getDeliveryId(), "DELIVERY_COMPLETED", deliveryCompletedTopic, event);
    }

    /**
     * Publish shipper status change event (BUSY/AVAILABLE)
     */
    public void publishShipperStatusChange(Long shipperId, String status, Long deliveryId, Long orderId) {
        log.info("📦 [Kafka] Sending shipper status change: shipper={}, status={}", shipperId, status);

        Map<String, Object> event = new HashMap<>();
        event.put("shipperId", shipperId);
        event.put("status", status);
        event.put("deliveryId", deliveryId);
        event.put("orderId", orderId);
        event.put("timestamp", System.currentTimeMillis());

        outboxService.saveEvent("DELIVERY", deliveryId.toString(), "SHIPPER_STATUS_CHANGE",
                shipperStatusChangeTopic, shipperId.toString(), event);
    }

    private void save(Long deliveryId, String eventType, String topic, Object event) {
        if (deliveryId == null) throw new IllegalArgumentException("deliveryId is required");
        outboxService.saveEvent("DELIVERY", deliveryId.toString(), eventType,
                topic, deliveryId.toString(), event);
    }
}
