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

import java.math.BigDecimal;

/**
 * Kafka Listener cho các events từ Order Service
 *
 * Topics:
 * - order.created → Đơn hàng mới
 * - order.status-updated → Trạng thái thay đổi (DELIVERED, PREPARING, etc.)
 * - order.cancelled → Đơn hàng bị hủy
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final EventProcessingService eventService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = KafkaTopicConstants.ORDER_CREATED, groupId = "analytics-service-group")
    public void onOrderCreated(@Payload String message, Acknowledgment ack) {
        try {
            log.info("📥 [Analytics] Received ORDER_CREATED event");
            JsonNode json = objectMapper.readTree(message);

            Long orderId = json.path("orderId").asLong();
            Long userId = json.path("userId").asLong();
            Long restaurantId = json.has("restaurantId") ? json.path("restaurantId").asLong() : null;
            String restaurantName = json.path("restaurantName").asText(null);
            BigDecimal totalPrice = json.has("totalPrice") && !json.path("totalPrice").isNull()
                    ? new BigDecimal(json.path("totalPrice").asText("0"))
                    : BigDecimal.ZERO;
            String paymentMethod = json.path("paymentMethod").asText(null);

            eventService.processOrderCreated(orderId, userId, restaurantId, restaurantName,
                    totalPrice, paymentMethod, message);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("❌ [Analytics] Failed to process ORDER_CREATED event: {}", e.getMessage(), e);
            ack.acknowledge(); // Don't block queue
        }
    }

    @KafkaListener(topics = KafkaTopicConstants.ORDER_STATUS_UPDATED, groupId = "analytics-service-group")
    public void onOrderStatusUpdated(@Payload String message, Acknowledgment ack) {
        try {
            log.info("📥 [Analytics] Received ORDER_STATUS_UPDATED event");
            JsonNode json = objectMapper.readTree(message);

            String newStatus = json.path("status").asText(json.path("newStatus").asText(""));
            Long orderId = json.path("orderId").asLong();
            Long restaurantId = json.has("restaurantId") ? json.path("restaurantId").asLong() : null;
            String restaurantName = json.path("restaurantName").asText(null);

            if ("DELIVERED".equalsIgnoreCase(newStatus)) {
                BigDecimal totalPrice = json.has("totalPrice") && !json.path("totalPrice").isNull()
                        ? new BigDecimal(json.path("totalPrice").asText("0"))
                        : BigDecimal.ZERO;
                eventService.processOrderDelivered(orderId, restaurantId, restaurantName, totalPrice, message);
            }
            // Các status khác (PREPARING, READY, etc.) có thể xử lý thêm nếu cần

            ack.acknowledge();
        } catch (Exception e) {
            log.error("❌ [Analytics] Failed to process ORDER_STATUS_UPDATED event: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = KafkaTopicConstants.ORDER_CANCELLED, groupId = "analytics-service-group")
    public void onOrderCancelled(@Payload String message, Acknowledgment ack) {
        try {
            log.info("📥 [Analytics] Received ORDER_CANCELLED event");
            JsonNode json = objectMapper.readTree(message);

            Long orderId = json.path("orderId").asLong();
            Long restaurantId = json.has("restaurantId") ? json.path("restaurantId").asLong() : null;

            eventService.processOrderCancelled(orderId, restaurantId, message);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("❌ [Analytics] Failed to process ORDER_CANCELLED event: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }
}
