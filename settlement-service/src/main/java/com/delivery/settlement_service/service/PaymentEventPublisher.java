package com.delivery.settlement_service.service;

import com.delivery.settlement_service.dto.event.PaymentEvent;
import com.delivery.settlement_service.entity.PaymentOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Aligned with KafkaTopicConstants in order-service
    private static final String TOPIC_PAYMENT_COMPLETED = "payment.completed";
    private static final String TOPIC_PAYMENT_FAILED = "payment.failed";

    public void publishPaymentSuccess(PaymentOrder order) {
        log.info("📣 Publishing PaymentSuccessEvent for ref: {}", order.getPaymentRef());
        
        PaymentEvent event = PaymentEvent.builder()
                .paymentId(order.getId())
                .orderId(order.getOrderId())
                .userId(order.getEntityId())
                .status("COMPLETED")
                .amount(order.getAmount().doubleValue())
                .paymentMethod(order.getProvider())
                .transactionId(order.getProviderTransactionId())
                .processedAt(LocalDateTime.now())
                .build();

        try {
            kafkaTemplate.send(TOPIC_PAYMENT_COMPLETED, order.getPaymentRef(), event);
            log.info("✅ Event sent to topic: {}", TOPIC_PAYMENT_COMPLETED);
        } catch (Exception e) {
            log.error("❌ Failed to send event to Kafka: {}", e.getMessage(), e);
        }
    }

    public void publishPaymentFailed(PaymentOrder order, String reason) {
        log.info("📣 Publishing PaymentFailedEvent for ref: {}", order.getPaymentRef());
        
        PaymentEvent event = PaymentEvent.builder()
                .paymentId(order.getId())
                .orderId(order.getOrderId())
                .userId(order.getEntityId())
                .status("FAILED")
                .amount(order.getAmount().doubleValue())
                .paymentMethod(order.getProvider())
                .processedAt(LocalDateTime.now())
                .failureReason(reason)
                .build();

        try {
            kafkaTemplate.send(TOPIC_PAYMENT_FAILED, order.getPaymentRef(), event);
        } catch (Exception e) {
            log.error("❌ Failed to send event to Kafka: {}", e.getMessage(), e);
        }
    }
}
