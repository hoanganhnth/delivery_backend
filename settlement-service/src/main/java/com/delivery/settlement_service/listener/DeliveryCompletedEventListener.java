package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.common.constants.KafkaTopicConstants;
import com.delivery.settlement_service.dto.event.DeliveryCompletedEvent;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.WalletType;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * ✅ Kafka listener: Tạo giao dịch khi đơn hàng giao thành công
 * 
 * Mô hình 2 Ví (Dual Wallet):
 * - Shipper Ví Thu nhập (EARNINGS): Tiền công giao hàng
 * - Shipper Ví Ký quỹ (DEPOSIT):   Đối trừ tiền COD thu hộ
 * - Restaurant: Chỉ dùng 1 ví (EARNINGS)
 * 
 * Idempotent: Kiểm tra orderId trước khi tạo giao dịch
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCompletedEventListener {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @KafkaListener(
            topics = KafkaTopicConstants.DELIVERY_COMPLETED_TOPIC,
            groupId = "settlement-service-group"
    )
    @Transactional
    public void handleDeliveryCompleted(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        DeliveryCompletedEvent event = null;
        try {
            event = objectMapper.readValue(message, DeliveryCompletedEvent.class);
            log.info("💰 Received DeliveryCompletedEvent: delivery={}, order={}, restaurant={}, shipper={}, " +
                            "restaurantEarnings={}, shipperEarnings={}, paymentMethod={}",
                    event.getDeliveryId(), event.getOrderId(), event.getRestaurantId(), event.getShipperId(),
                    event.getRestaurantEarnings(), event.getShipperEarnings(), event.getPaymentMethod());

            // ── Validate ──────────────────────────────────────────
            if (event.getRestaurantId() == null || event.getShipperId() == null) {
                log.error("💥 Invalid event: restaurantId or shipperId is null");
                acknowledgment.acknowledge();
                return;
            }

            if (event.getRestaurantEarnings() == null || event.getRestaurantEarnings().compareTo(BigDecimal.ZERO) <= 0) {
                log.error("💥 Invalid event: restaurantEarnings is null or <= 0");
                acknowledgment.acknowledge();
                return;
            }

            if (event.getShipperEarnings() == null || event.getShipperEarnings().compareTo(BigDecimal.ZERO) <= 0) {
                log.error("💥 Invalid event: shipperEarnings is null or <= 0");
                acknowledgment.acknowledge();
                return;
            }

            try {
                // ── Idempotency Check ─────────────────────────────
                if (transactionRepository.existsByOrderIdAndEntityIdAndEntityTypeAndReason(
                        event.getOrderId(), event.getRestaurantId(),
                        EntityType.RESTAURANT, TransactionReason.ORDER_EARNING)) {
                    log.warn("⚠️ [Idempotent] Order {} already processed, skipping", event.getOrderId());
                    acknowledgment.acknowledge();
                    return;
                }

                boolean isCOD = "COD".equalsIgnoreCase(event.getPaymentMethod());

                // ══════════════════════════════════════════════════
                // 1. RESTAURANT — Ví Thu nhập (EARNINGS)
                // ══════════════════════════════════════════════════

                // 1a. CREDIT: Doanh thu thực nhận (đã trừ hoa hồng)
                transactionService.createTransaction(
                        event.getRestaurantId(),
                        EntityType.RESTAURANT,
                        event.getOrderId(),
                        TransactionDirection.CREDIT,
                        TransactionReason.ORDER_EARNING,
                        event.getRestaurantEarnings(),
                        "Doanh thu đơn #" + event.getOrderId() + " (đã trừ hoa hồng)",
                        WalletType.EARNINGS
                );

                log.info("✅ Restaurant {} credited {} for order {}",
                        event.getRestaurantId(), event.getRestaurantEarnings(), event.getOrderId());

                // 1b. DEBIT: Ghi nhận hoa hồng (chỉ ghi sổ, không trừ tiền thực tế)
                if (event.getRestaurantCommission() != null && event.getRestaurantCommission().compareTo(BigDecimal.ZERO) > 0) {
                    transactionService.createTransaction(
                            event.getRestaurantId(),
                            EntityType.RESTAURANT,
                            event.getOrderId(),
                            TransactionDirection.DEBIT,
                            TransactionReason.PLATFORM_COMMISSION,
                            event.getRestaurantCommission(),
                            "Hoa hồng nền tảng (20% giá món) đơn #" + event.getOrderId(),
                            WalletType.EARNINGS
                    );
                }

                // ══════════════════════════════════════════════════
                // 2. SHIPPER — Ví Thu nhập (EARNINGS): Tiền công giao hàng
                // ══════════════════════════════════════════════════

                transactionService.createTransaction(
                        event.getShipperId(),
                        EntityType.SHIPPER,
                        event.getOrderId(),
                        TransactionDirection.CREDIT,
                        TransactionReason.DELIVERY_FEE,
                        event.getShipperEarnings(),
                        "Tiền công giao đơn #" + event.getOrderId(),
                        WalletType.EARNINGS
                );

                log.info("✅ Shipper {} credited {} to Earnings for order {}",
                        event.getShipperId(), event.getShipperEarnings(), event.getOrderId());

                // ══════════════════════════════════════════════════
                // 3. SHIPPER (COD only) — Ví Ký quỹ (DEPOSIT): Đối trừ tiền thu hộ
                // ══════════════════════════════════════════════════

                if (isCOD) {
                    // totalCollected = Tiền món (net + commission) + Phí ship
                    // = Tổng số tiền mặt shipper thu từ khách
                    BigDecimal totalCollected = event.getRestaurantEarnings()
                            .add(event.getRestaurantCommission() != null ? event.getRestaurantCommission() : BigDecimal.ZERO)
                            .add(event.getShippingFee() != null ? event.getShippingFee() : BigDecimal.ZERO);

                    transactionService.createTransaction(
                            event.getShipperId(),
                            EntityType.SHIPPER,
                            event.getOrderId(),
                            TransactionDirection.DEBIT,
                            TransactionReason.COD_SETTLEMENT,
                            totalCollected,
                            "Đối trừ COD đơn #" + event.getOrderId() + " (shipper đã thu " + totalCollected + " tiền mặt)",
                            WalletType.DEPOSIT
                    );

                    log.info("💵 Shipper {} COD settlement: -{} from Deposit for order {}",
                            event.getShipperId(), totalCollected, event.getOrderId());
                }

            } catch (Exception e) {
                log.error("💥 Failed to create transactions for delivery {}: {}",
                        event.getDeliveryId(), e.getMessage(), e);
                // Don't acknowledge to retry
                return;
            }

            // Acknowledge after successful processing
            acknowledgment.acknowledge();
            log.info("✅ Successfully processed DeliveryCompletedEvent for delivery {}", event.getDeliveryId());

        } catch (Exception e) {
            log.error("💥 Unexpected error processing DeliveryCompletedEvent for delivery: {} - Error: {}",
                    event != null ? event.getDeliveryId() : "unknown", e.getMessage(), e);

            // Acknowledge to prevent infinite retry
            acknowledgment.acknowledge();
        }
    }
}
