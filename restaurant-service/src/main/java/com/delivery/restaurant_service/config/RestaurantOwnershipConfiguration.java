package com.delivery.restaurant_service.config;

import com.delivery.restaurant.application.DefaultRestaurantOwnerAssignmentUseCase;
import com.delivery.restaurant.application.api.PrincipalOwnershipDirectory;
import com.delivery.restaurant.application.api.RestaurantOwnerAssignmentUseCase;
import com.delivery.restaurant.domain.catalog.MenuItemLifecyclePolicy;
import com.delivery.restaurant.domain.catalog.RestaurantLifecyclePolicy;
import java.time.Clock;
import com.delivery.restaurant.infrastructure.identity.IdentityInfrastructureConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Import(IdentityInfrastructureConfiguration.class)
public class RestaurantOwnershipConfiguration {

    @Bean
    RestaurantOwnerAssignmentUseCase restaurantOwnerAssignmentUseCase(
            PrincipalOwnershipDirectory principalDirectory) {
        return new DefaultRestaurantOwnerAssignmentUseCase(principalDirectory);
    }

    @Bean
    RestaurantLifecyclePolicy restaurantLifecyclePolicy() {
        return new RestaurantLifecyclePolicy();
    }

    @Bean
    MenuItemLifecyclePolicy menuItemLifecyclePolicy() {
        return new MenuItemLifecyclePolicy();
    }

    @Bean
    Clock catalogClock() {
        return Clock.systemUTC();
    }
}
