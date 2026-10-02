package com.delivery.restaurant.infrastructure.rating;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import com.delivery.restaurant.application.DefaultRestaurantRatingUseCase;
import com.delivery.restaurant.application.api.*;

/** Explicit host wiring for rating persistence and concurrency adapters. */
@Configuration
@Import({JpaRestaurantRatingAdapter.class, RestaurantRatingLock.class})
public class RestaurantRatingInfrastructureConfiguration {
    @Bean RestaurantTransactionPort restaurantTransactions(PlatformTransactionManager manager) {
        return new SpringRestaurantTransactionAdapter(manager);
    }
    @Bean RestaurantRatingUseCase restaurantRatingUseCase(RestaurantRatingStorePort ratings,
            RatingOrderEligibilityPort orders, RestaurantTransactionPort transactions) {
        return new DefaultRestaurantRatingUseCase(ratings, orders, transactions);
    }
}
