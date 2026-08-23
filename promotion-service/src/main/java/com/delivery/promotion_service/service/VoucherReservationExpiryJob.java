package com.delivery.promotion_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.promotion.checkout-enabled", havingValue = "true")
public class VoucherReservationExpiryJob {
    private final PromotionService promotionService;

    @Scheduled(fixedDelayString = "${app.promotion.reservation-expiry-scan-ms:30000}")
    public void expireReservations() {
        int legacyExpired = promotionService.expireReservations();
        int stackedExpired = promotionService.expirePromotionReservations();
        int expired = legacyExpired + stackedExpired;
        if (expired > 0) {
            log.info("Expired {} voucher reservation(s) (legacy={}, stacked={})",
                    expired, legacyExpired, stackedExpired);
        }
    }
}
