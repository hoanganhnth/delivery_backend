package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.common.constants.KafkaTopicConstants;
import com.delivery.settlement_service.dto.event.DeliveryPickedUpEvent;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Kafka listener xử lý COD deduction khi shipper lấy hàng.
 * 
 * Luồng COD (tiền mặt):
 *   Shipper lấy hàng → trừ ví shipper (COD_DEDUCTION)
 *   Shipper sẽ thu tiền mặt từ khách khi giao → tự bù lại
 * 
 * Luồng Pre-paid (chuyển khoản):
 *   Bỏ qua, không cần trừ gì
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryPickedUpEventListener {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @KafkaListener(
            topics = KafkaTopicConstants.DELIVERY_PICKED_UP_TOPIC,
            groupId = "settlement-service-group"
    )
    @Transactional
    public void handleDeliveryPickedUp(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        DeliveryPickedUpEvent event = null;
        try {
            event = objectMapper.readValue(message, DeliveryPickedUpEvent.class);
            log.info("📦 Received DeliveryPickedUpEvent: delivery={}, order={}, shipper={}, paymentMethod={}",
                    event.getDeliveryId(), event.getOrderId(), event.getShipperId(), event.getPaymentMethod());

            // ✅ Chỉ xử lý COD — Pre-paid không cần trừ ví shipper
            if (!"COD".equalsIgnoreCase(event.getPaymentMethod())) {
                log.info("💳 Order {} is pre-paid ({}), skipping COD deduction",
                        event.getOrderId(), event.getPaymentMethod());
                acknowledgment.acknowledge();
                return;
            }

            // Validate
            if (event.getShipperId() == null) {
                log.error("💥 Invalid DeliveryPickedUpEvent: shipperId is null");
                acknowledgment.acknowledge();
                return;
            }

            // ✅ IDEMPOTENCY: Đã trừ COD cho order này chưa?
            if (transactionRepository.existsByOrderIdAndEntityIdAndEntityTypeAndReason(
                    event.getOrderId(), event.getShipperId(),
                    EntityType.SHIPPER, TransactionReason.COD_DEDUCTION)) {
                log.warn("⚠️ [Idempotent] Shipper {} already COD-deducted for order {}, skipping",
                        event.getShipperId(), event.getOrderId());
                acknowledgment.acknowledge();
                return;
            }

            // ✅ Tính khoản trừ COD = totalPrice - shipperEarnings
            // Shipper sẽ thu totalPrice tiền mặt, nhưng chỉ được giữ shipperEarnings
            // Phần còn lại (tiền vốn + hoa hồng) phải trả lại cho nền tảng
            BigDecimal codDeduction = BigDecimal.ZERO;
            if (event.getTotalPrice() != null && event.getShipperEarnings() != null) {
                codDeduction = event.getTotalPrice().subtract(event.getShipperEarnings());
            } else if (event.getTotalPrice() != null) {
                codDeduction = event.getTotalPrice();
            }

            if (codDeduction.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ COD deduction is zero or negative for order {}, skipping", event.getOrderId());
                acknowledgment.acknowledge();
                return;
            }

            // ✅ DEBIT shipper — trừ tiền thu hộ COD
            transactionService.createTransaction(
                    event.getShipperId(),
                    EntityType.SHIPPER,
                    event.getOrderId(),
                    TransactionDirection.DEBIT,
                    TransactionReason.COD_DEDUCTION,
                    codDeduction,
                    "Thu hộ COD đơn #" + event.getOrderId() +
                            " (Tổng: " + event.getTotalPrice() + "đ, Shipper giữ: " + event.getShipperEarnings() + "đ)"
            );

            log.info("✅ COD deduction {} from shipper {} for order {} (Total: {}, ShipperKeeps: {})",
                    codDeduction, event.getShipperId(), event.getOrderId(),
                    event.getTotalPrice(), event.getShipperEarnings());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("💥 Error processing DeliveryPickedUpEvent: {}", e.getMessage(), e);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }
}
