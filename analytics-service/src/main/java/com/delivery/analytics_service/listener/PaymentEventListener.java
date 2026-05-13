package com.delivery.analytics_service.listener;

import com.delivery.analytics_service.common.KafkaTopicConstants;
import com.delivery.analytics_service.service.EventProcessingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka Listener cho events từ Settlement Service (Payment)
 *
 * Topics:
 * - payment.completed → Thanh toán thành công
 * - payment.failed → Thanh toán thất bại
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final EventProcessingService eventService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = KafkaTopicConstants.PAYMENT_COMPLETED, groupId = "analytics-service-group")
    public void onPaymentCompleted(@Payload String message, Acknowledgment ack) {
        try {
            log.info("📥 [Analytics] Received PAYMENT_COMPLETED event");
            JsonNode json = objectMapper.readTree(message);

            Long orderId = json.path("orderId").asLong();
            Long userId = json.path("userId").asLong();
            Double amount = json.has("amount") ? json.path("amount").asDouble() : 0.0;
            String paymentMethod = json.path("paymentMethod").asText(null);

            eventService.processPaymentCompleted(orderId, userId, amount, paymentMethod, message);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("❌ [Analytics] Failed to process PAYMENT_COMPLETED: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = KafkaTopicConstants.PAYMENT_FAILED, groupId = "analytics-service-group")
    public void onPaymentFailed(@Payload String message, Acknowledgment ack) {
        try {
            log.info("📥 [Analytics] Received PAYMENT_FAILED event");
            JsonNode json = objectMapper.readTree(message);

            Long orderId = json.path("orderId").asLong();

            eventService.processPaymentFailed(orderId, message);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("❌ [Analytics] Failed to process PAYMENT_FAILED: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }
}
