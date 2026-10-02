package com.delivery.restaurant.infrastructure.transaction;

import com.delivery.restaurant.application.api.RestaurantTransactionPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class RestaurantTransactionConfiguration {
    @Bean RestaurantTransactionPort restaurantTransactions(PlatformTransactionManager manager) {
        return new SpringRestaurantTransactionAdapter(manager);
    }
}
