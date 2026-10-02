package com.delivery.restaurant_service.config;

import com.delivery.restaurant.application.api.OrderValidationUseCase;
import com.delivery.restaurant.application.api.OrderValidationCatalogPort;
import com.delivery.restaurant.application.api.RestaurantServiceabilityUseCase;
import com.delivery.restaurant.application.api.MenuItemInventoryUseCase;
import org.springframework.beans.factory.ObjectProvider;
import com.delivery.restaurant.application.DefaultOrderValidationUseCase;
import org.springframework.beans.factory.annotation.Value;
import com.delivery.restaurant.application.DefaultCatalogLifecycleUseCase;
import com.delivery.restaurant.application.api.RestaurantTransactionPort;
import com.delivery.restaurant.application.api.CatalogLifecycleEffectsPort;
import com.delivery.restaurant.application.api.CatalogLifecycleStorePort;
import com.delivery.restaurant.application.api.CatalogLifecycleUseCase;
import com.delivery.restaurant.application.DefaultCatalogLifecycleDecisionUseCase;
import com.delivery.restaurant.application.DefaultCreateMenuItemUseCase;
import com.delivery.restaurant.application.DefaultMenuItemManagementReadUseCase;
import com.delivery.restaurant.application.DefaultMenuItemReadUseCase;
import com.delivery.restaurant.application.DefaultUpdateMenuItemUseCase;
import com.delivery.restaurant.application.DefaultRestaurantManagementAccessUseCase;
import com.delivery.restaurant.application.DefaultRestaurantOwnerAssignmentUseCase;
import com.delivery.restaurant.application.DefaultCreateRestaurantUseCase;
import com.delivery.restaurant.application.DefaultRestaurantManagementReadUseCase;
import com.delivery.restaurant.application.DefaultRestaurantReadUseCase;
import com.delivery.restaurant.application.DefaultRestaurantUpdateUseCase;
import com.delivery.restaurant.application.api.CreateRestaurantUseCase;
import com.delivery.restaurant.application.api.CreateMenuItemUseCase;
import com.delivery.restaurant.application.api.MenuItemCreationPort;
import com.delivery.restaurant.application.api.MenuItemManagementReadUseCase;
import com.delivery.restaurant.application.api.MenuItemReadPort;
import com.delivery.restaurant.application.api.MenuItemReadUseCase;
import com.delivery.restaurant.application.api.MenuItemUpdatePort;
import com.delivery.restaurant.application.api.UpdateMenuItemUseCase;
import com.delivery.restaurant.application.api.RestaurantCreationPort;
import com.delivery.restaurant.application.api.CatalogLifecycleDecisionUseCase;
import com.delivery.restaurant.application.api.PrincipalOwnershipDirectory;
import com.delivery.restaurant.application.api.RestaurantOwnerAssignmentUseCase;
import com.delivery.restaurant.application.api.RestaurantManagementAccessUseCase;
import com.delivery.restaurant.application.api.RestaurantManagementReadUseCase;
import com.delivery.restaurant.application.api.RestaurantReadPort;
import com.delivery.restaurant.application.api.RestaurantReadUseCase;
import com.delivery.restaurant.application.api.RestaurantUpdatePort;
import com.delivery.restaurant.application.api.UpdateRestaurantUseCase;
import com.delivery.restaurant.domain.catalog.MenuItemLifecyclePolicy;
import com.delivery.restaurant.domain.catalog.RestaurantLifecyclePolicy;
import java.time.Clock;
import com.delivery.restaurant.infrastructure.decision.RestaurantDecisionInfrastructureConfiguration;
import com.delivery.restaurant.infrastructure.identity.IdentityInfrastructureConfiguration;
import com.delivery.restaurant.infrastructure.client.OrderInfrastructureConfiguration;
import com.delivery.restaurant.infrastructure.rating.RestaurantRatingInfrastructureConfiguration;
import com.delivery.restaurant.infrastructure.serviceability.RestaurantServiceabilityInfrastructureConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Import({IdentityInfrastructureConfiguration.class, OrderInfrastructureConfiguration.class,
        RestaurantDecisionInfrastructureConfiguration.class, RestaurantRatingInfrastructureConfiguration.class,
        RestaurantServiceabilityInfrastructureConfiguration.class})
public class RestaurantOwnershipConfiguration {

    @Bean
    OrderValidationUseCase orderValidationUseCase(
            OrderValidationCatalogPort catalog,
            RestaurantServiceabilityUseCase serviceability,
            ObjectProvider<MenuItemInventoryUseCase> inventory,
            Clock clock, RestaurantTransactionPort transactions) {
        return new DefaultOrderValidationUseCase(
                catalog, serviceability, inventory::getIfAvailable, clock, transactions);
    }


    @Bean
    CreateRestaurantUseCase createRestaurantUseCase(
            RestaurantOwnerAssignmentUseCase ownerAssignment, RestaurantCreationPort creationPort) {
        return new DefaultCreateRestaurantUseCase(ownerAssignment, creationPort);
    }

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
    CatalogLifecycleDecisionUseCase catalogLifecycleDecisionUseCase(
            RestaurantLifecyclePolicy restaurantPolicy,
            MenuItemLifecyclePolicy menuItemPolicy) {
        return new DefaultCatalogLifecycleDecisionUseCase(restaurantPolicy, menuItemPolicy);
    }

    @Bean
    RestaurantManagementAccessUseCase restaurantManagementAccessUseCase() {
        return new DefaultRestaurantManagementAccessUseCase();
    }

    @Bean
    UpdateRestaurantUseCase updateRestaurantUseCase(
            RestaurantUpdatePort updatePort,
            RestaurantManagementAccessUseCase managementAccessUseCase) {
        return new DefaultRestaurantUpdateUseCase(updatePort, managementAccessUseCase);
    }

    @Bean
    RestaurantReadUseCase restaurantReadUseCase(RestaurantReadPort readPort) {
        return new DefaultRestaurantReadUseCase(readPort);
    }

    @Bean
    RestaurantManagementReadUseCase restaurantManagementReadUseCase(RestaurantReadPort readPort) {
        return new DefaultRestaurantManagementReadUseCase(readPort);
    }

    @Bean
    CreateMenuItemUseCase createMenuItemUseCase(
            MenuItemCreationPort creationPort,
            RestaurantManagementAccessUseCase managementAccessUseCase) {
        return new DefaultCreateMenuItemUseCase(creationPort, managementAccessUseCase);
    }

    @Bean
    UpdateMenuItemUseCase updateMenuItemUseCase(
            MenuItemUpdatePort updatePort,
            RestaurantManagementAccessUseCase managementAccessUseCase) {
        return new DefaultUpdateMenuItemUseCase(updatePort, managementAccessUseCase);
    }

    @Bean
    MenuItemReadUseCase menuItemReadUseCase(MenuItemReadPort readPort) {
        return new DefaultMenuItemReadUseCase(readPort);
    }

    @Bean
    MenuItemManagementReadUseCase menuItemManagementReadUseCase(MenuItemReadPort readPort) {
        return new DefaultMenuItemManagementReadUseCase(readPort);
    }

    @Bean
    Clock catalogClock() {
        return Clock.systemUTC();
    }
    @Bean
    CatalogLifecycleUseCase catalogLifecycleUseCase(
            CatalogLifecycleStorePort store,
            CatalogLifecycleEffectsPort effects,
            CatalogLifecycleDecisionUseCase decisions, RestaurantManagementAccessUseCase access,
            RestaurantTransactionPort transactions,
            @Value("${app.identity.principal-ownership.enforced:false}") boolean enforced) {
        return new DefaultCatalogLifecycleUseCase(store, effects, decisions, access, transactions, enforced);
    }
}
