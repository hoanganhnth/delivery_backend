package com.delivery.restaurant.infrastructure.serviceability;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Explicit host wiring for serviceability and inventory infrastructure adapters. */
@Configuration
@Import({RestaurantServiceabilityService.class,
        com.delivery.restaurant.infrastructure.inventory.MenuItemInventoryReservationService.class,
        com.delivery.restaurant.infrastructure.inventory.MenuItemInventoryOrderEventProcessor.class})
public class RestaurantServiceabilityInfrastructureConfiguration {
}
