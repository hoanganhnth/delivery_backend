package com.delivery.delivery_service.service;

import com.delivery.delivery_service.common.constants.KafkaTopicConstants;
import com.delivery.delivery_service.dto.event.FindShipperEvent;
import com.delivery.delivery_service.dto.event.MatchAcceptedEvent;
import com.delivery.delivery_service.dto.event.DeliveryCancelledEvent;
import com.delivery.delivery_service.dto.event.DeliveryCompletedEvent;
import lombok.extern.slf4j.Slf4j;
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

    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    public DeliveryEventPublisher(org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Gửi FindShipperEvent đến Match Service
     */
    public void publishFindShipperEvent(FindShipperEvent event) {
        log.info("📦 [Kafka] Sending FindShipperEvent for delivery: {}", event.getDeliveryId());
        kafkaTemplate.send(
                KafkaTopicConstants.FIND_SHIPPER_TOPIC,
                event.getDeliveryId().toString(),
                event
        );
    }

    /**
     * Gửi ShipperAcceptedEvent
     */
    public void publishShipperAcceptedEvent(MatchAcceptedEvent event) {
        log.info("📦 [Kafka] Sending ShipperAcceptedEvent for order: {}, shipper: {}",
                event.getOrderId(), event.getShipperId());
        kafkaTemplate.send(
                KafkaTopicConstants.SHIPPER_ACCEPTED_TOPIC,
                event.getOrderId().toString(),
                event
        );
    }

    /**
     * Gửi delivery status update event
     */
    public void publishDeliveryStatusUpdated(Long deliveryId, Long orderId, String status, String previousStatus) {
        log.info("📦 [Kafka] Sending delivery status update: {} -> {} for delivery: {}, order: {}",
                previousStatus, status, deliveryId, orderId);

        DeliveryStatusUpdateEvent statusEvent = new DeliveryStatusUpdateEvent(
                deliveryId, orderId, status, previousStatus
        );

        kafkaTemplate.send(
                KafkaTopicConstants.DELIVERY_STATUS_UPDATED_TOPIC,
                deliveryId.toString(),
                statusEvent
        );
    }

    /**
     * Inner class for delivery status update events
     */
    public static class DeliveryStatusUpdateEvent {
        public final Long deliveryId;
        public final Long orderId;
        public final String newStatus;
        public final String oldStatus;
        public final String eventType = "DELIVERY_STATUS_UPDATED";
        public final LocalDateTime timestamp = LocalDateTime.now();

        public DeliveryStatusUpdateEvent(Long deliveryId, Long orderId, String newStatus, String oldStatus) {
            this.deliveryId = deliveryId;
            this.orderId = orderId;
            this.newStatus = newStatus;
            this.oldStatus = oldStatus;
        }
    }

    /**
     * Publish DeliveryCancelledEvent
     */
    public void publishDeliveryCancelledEvent(DeliveryCancelledEvent event) {
        log.info("📦 [Kafka] Sending DeliveryCancelledEvent for delivery: {}, order: {}",
                event.getDeliveryId(), event.getOrderId());
        kafkaTemplate.send(
                KafkaTopicConstants.DELIVERY_CANCELLED_TOPIC,
                event.getDeliveryId().toString(),
                event
        );
    }

    /**
     * ✅ Publish DeliveryCompletedEvent — SỰ KIỆN QUAN TRỌNG NHẤT (liên quan đến tiền)
     */
    public void publishDeliveryCompletedEvent(DeliveryCompletedEvent event) {
        log.info("📦 [Kafka] Sending DeliveryCompletedEvent for delivery: {}, shipper: {}, amount: {}",
                event.getDeliveryId(), event.getShipperId(), event.getShippingFee());
        kafkaTemplate.send(
                KafkaTopicConstants.DELIVERY_COMPLETED_TOPIC,
                event.getDeliveryId().toString(),
                event
        );
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

        kafkaTemplate.send(
                KafkaTopicConstants.SHIPPER_STATUS_CHANGE_TOPIC,
                shipperId.toString(),
                event
        );
    }
}
