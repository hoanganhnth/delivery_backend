package com.delivery.restaurant_service.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant.application.api.MenuItemInventoryUseCase;
import com.delivery.restaurant.application.api.RestaurantServiceabilityUseCase;
import com.delivery.restaurant.application.api.ServiceabilityDecision;
import com.delivery.restaurant_service.service.OrderValidationHttpAdapter;
import com.delivery.restaurant_service.service.JpaOrderValidationCatalogAdapter;
import com.delivery.restaurant.application.DefaultOrderValidationUseCase;
import com.delivery.restaurant.application.api.RestaurantTransactionPort;
import java.util.function.Supplier;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class OrderValidationPersistenceIntegrationTest {

    @Autowired RestaurantRepository restaurants;
    @Autowired MenuItemRepository menuItems;

    @Test
    void validationUsesPersistedPriceAndParentLifecycleFromOneDatabaseBoundary() {
        Restaurant restaurant = restaurant(RestaurantStatus.ACTIVE);
        restaurant = restaurants.saveAndFlush(restaurant);
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        item.setName("Canonical dish");
        item.setPrice(new BigDecimal("42.50"));
        item.setStatus(MenuItem.Status.AVAILABLE);
        item = menuItems.saveAndFlush(item);

        var service = validator();
        var request = OrderValidationRequest.builder().restaurantId(restaurant.getId()).items(List.of(
                OrderValidationRequest.OrderItemRequest.builder().menuItemId(item.getId())
                        .menuItemName("client value").price(0.01).quantity(2).build())).build();

        var result = service.validateOrderFromOrderService(request);

        assertThat(result.getIsValid()).isTrue();
        assertThat(result.getCalculatedTotal()).isEqualTo(85.0);
        assertThat(result.getItemValidations()).singleElement().extracting("menuItemName")
                .isEqualTo("Canonical dish");

        restaurant.setLifecycleStatus(RestaurantStatus.ARCHIVED);
        restaurants.saveAndFlush(restaurant);

        var archived = service.validateOrderFromOrderService(request);
        assertThat(archived.getIsValid()).isFalse();
        assertThat(archived.getErrors()).extracting("errorCode")
                .contains("RESTAURANT_NOT_ACCEPTING_ORDERS");
    }

    private OrderValidationHttpAdapter validator() {
        RestaurantServiceabilityUseCase serviceability = mock(RestaurantServiceabilityUseCase.class);
        when(serviceability.evaluate(anyLong(), any(), any()))
                .thenReturn(new ServiceabilityDecision(false, false, null, null, "CAPABILITY_DISABLED"));
        @SuppressWarnings("unchecked")
        ObjectProvider<MenuItemInventoryUseCase> inventory = mock(ObjectProvider.class);
        when(inventory.getIfAvailable()).thenReturn(null);
        return new OrderValidationHttpAdapter(new DefaultOrderValidationUseCase(
                new JpaOrderValidationCatalogAdapter(restaurants, menuItems), serviceability, inventory::getIfAvailable,
                Clock.fixed(Instant.parse("2026-01-01T05:00:00Z"), ZoneOffset.UTC), new RestaurantTransactionPort() {
                    @Override public <T> T required(Supplier<T> operation) { return operation.get(); }
                    @Override public <T> T readOnly(Supplier<T> operation) { return operation.get(); }
                    @Override public <T> T repeatableRead(Supplier<T> operation) { return operation.get(); }
                }));
    }

    private Restaurant restaurant(RestaurantStatus status) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("Persistence restaurant");
        restaurant.setCreatorId(10L);
        restaurant.setLifecycleStatus(status);
        restaurant.setTimeZone("UTC");
        return restaurant;
    }
}
