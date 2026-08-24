package com.delivery.restaurant_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded expiry sweep; the database state machine remains the authority. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.restaurant.inventory-enabled", havingValue = "true")
public class MenuItemInventoryReservationExpiryScheduler {

    private final MenuItemInventoryReservationService reservationService;

    @Scheduled(fixedDelayString = "${app.restaurant.inventory-expiry-delay-ms:60000}")
    public void expire() {
        int expired = reservationService.expireReservations();
        if (expired > 0) log.info("Expired {} menu inventory reservations", expired);
    }
}
