package com.delivery.restaurant.infrastructure.serviceability;

import com.delivery.restaurant.application.DefaultRestaurantServiceabilityUseCase;
import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.infrastructure.transaction.RestaurantTransactionConfiguration;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Explicit composition for the serviceability core and its technical adapters. */
@Configuration
@Import({JpaServiceabilityAdapter.class, RestaurantTransactionConfiguration.class,
        com.delivery.restaurant.infrastructure.inventory.MenuItemInventoryReservationService.class,
        com.delivery.restaurant.infrastructure.inventory.MenuItemInventoryOrderEventProcessor.class})
public class RestaurantServiceabilityInfrastructureConfiguration {
    @Bean ServiceabilityPolygonPort serviceabilityPolygons() {
        return GeoJsonServiceabilityPolygonAdapter::parsePolygon;
    }
    @Bean ServiceabilityIncidentPort serviceabilityIncidents() {
        var logger = LoggerFactory.getLogger(DefaultRestaurantServiceabilityUseCase.class);
        return (zoneId, restaurantId) -> logger.error("Invalid serviceability zone {} for restaurant {}", zoneId, restaurantId);
    }
    @Bean RestaurantServiceabilityUseCase restaurantServiceabilityUseCase(ServiceabilityStorePort store,
            ServiceabilityPolygonPort polygons, ServiceabilityIncidentPort incidents, RestaurantTransactionPort transactions,
            @Value("${app.restaurant.serviceability-enabled:false}") boolean enabled,
            @Value("${app.identity.principal-ownership.enforced:false}") boolean enforced) {
        return new DefaultRestaurantServiceabilityUseCase(store, polygons, incidents, transactions, enabled, enforced);
    }
}
