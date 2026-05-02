package com.delivery.promotion_service.service;

import com.delivery.promotion_service.entity.UserVoucher;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.repository.UserVoucherRepository;
import com.delivery.promotion_service.repository.VoucherRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaPromotionListener {

    private final UserVoucherRepository userVoucherRepository;
    private final VoucherRepository voucherRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.events", groupId = "promotion-service-group")
    @Transactional
    public void handleOrderEvents(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.path("eventType").asText();
            Long orderId = root.path("orderId").asLong();

            if ("ORDER_CREATED".equals(eventType) || "PAYMENT_COMPLETED".equals(eventType)) {
                // If payment is completed, commit the voucher (mark as USED)
                if ("PAYMENT_COMPLETED".equals(eventType)) {
                    commitVouchers(orderId);
                }
            } else if ("ORDER_CANCELLED".equals(eventType) || "PAYMENT_FAILED".equals(eventType)) {
                // Rollback / Release vouchers
                releaseVouchers(orderId);
            }
        } catch (Exception e) {
            log.error("Error processing Kafka message: {}", message, e);
        }
    }

    private void commitVouchers(Long orderId) {
        List<UserVoucher> reservedVouchers = userVoucherRepository.findByOrderId(orderId);
        for (UserVoucher uv : reservedVouchers) {
            if (uv.getStatus() == UserVoucher.Status.RESERVED) {
                uv.setStatus(UserVoucher.Status.USED);
                userVoucherRepository.save(uv);
                log.info("Committed voucher {} for order {}", uv.getVoucherId(), orderId);
            }
        }
    }

    private void releaseVouchers(Long orderId) {
        List<UserVoucher> reservedVouchers = userVoucherRepository.findByOrderId(orderId);
        for (UserVoucher uv : reservedVouchers) {
            if (uv.getStatus() == UserVoucher.Status.RESERVED) {
                uv.setStatus(UserVoucher.Status.SAVED);
                uv.setOrderId(null);
                userVoucherRepository.save(uv);

                Voucher voucher = voucherRepository.findById(uv.getVoucherId()).orElse(null);
                if (voucher != null) {
                    voucher.setUsedQuantity(Math.max(0, voucher.getUsedQuantity() - 1));
                    voucherRepository.save(voucher);
                }
                log.info("Released voucher {} for order {}", uv.getVoucherId(), orderId);
            }
        }
    }
}
