package com.delivery.restaurant.infrastructure.inventory;

import com.delivery.restaurant.application.DefaultMenuItemInventoryUseCase;
import com.delivery.restaurant.application.DefaultInventoryOrderEventUseCase;
import com.delivery.restaurant.application.api.InventoryOrderEventUseCase;
import com.delivery.restaurant.application.api.InventoryReceiptPort;
import com.delivery.restaurant.application.api.InventoryStorePort;
import com.delivery.restaurant.application.api.MenuItemInventoryUseCase;
import com.delivery.restaurant.application.api.RestaurantTransactionPort;
import com.delivery.restaurant.infrastructure.transaction.RestaurantTransactionConfiguration;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(name = "app.restaurant.inventory-enabled", havingValue = "true")
@Import({JpaInventoryAdapter.class, JpaInventoryReceiptAdapter.class, JsonInventoryOrderEventAdapter.class, RestaurantTransactionConfiguration.class})
public class RestaurantInventoryInfrastructureConfiguration {
    @Bean MenuItemInventoryUseCase menuItemInventoryUseCase(InventoryStorePort store, RestaurantTransactionPort transactions,
            @Value("${app.restaurant.inventory-reservation-ttl:PT15M}") Duration ttl) {
        return new DefaultMenuItemInventoryUseCase(store, transactions, ttl, Clock.systemDefaultZone());
    }
    @Bean InventoryOrderEventUseCase inventoryOrderEvents(InventoryReceiptPort receipts,
            MenuItemInventoryUseCase inventory, RestaurantTransactionPort transactions) {
        return new DefaultInventoryOrderEventUseCase(receipts, inventory, transactions);
    }
}
