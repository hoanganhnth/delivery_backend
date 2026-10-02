package com.delivery.restaurant.infrastructure.decision;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import com.delivery.restaurant.application.DefaultRestaurantOrderDecisionUseCase;
import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.infrastructure.transaction.RestaurantTransactionConfiguration;

/** Explicit host wiring for the restaurant order decision and outbox adapter. */
@Configuration
@Import({JpaRestaurantDecisionAdapter.class, RestaurantDecisionLock.class, RestaurantTransactionConfiguration.class})
public class RestaurantDecisionInfrastructureConfiguration {
    @Bean RestaurantOrderDecisionUseCase restaurantOrderDecisionUseCase(RestaurantDecisionStorePort decisions,
            OrderDecisionEligibilityPort orders, RestaurantTransactionPort transactions) {
        return new DefaultRestaurantOrderDecisionUseCase(decisions, orders, transactions);
    }
}
