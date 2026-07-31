package com.delivery.flashsale_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "app.flashsale.checkout-enabled", havingValue = "true")
public class FlashSaleReservationExpiryJob {
    private final FlashSaleStockService stockService;

    @Scheduled(fixedDelayString = "${app.flashsale.reservation-expiry-scan-ms:30000}")
    public void expire() {
        int count = stockService.expireReservations();
        if (count > 0) log.info("Expired {} flash-sale reservation(s)", count);
    }
}
