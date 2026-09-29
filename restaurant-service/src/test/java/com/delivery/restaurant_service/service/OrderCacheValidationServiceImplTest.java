package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.InventoryAvailability;
import com.delivery.restaurant.application.api.MenuItemInventoryUseCase;
import com.delivery.restaurant.application.api.RestaurantServiceabilityUseCase;
import com.delivery.restaurant.application.api.ServiceabilityDecision;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.impl.OrderCacheValidationServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderCacheValidationServiceImplTest {

    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final MenuItemRepository items = mock(MenuItemRepository.class);
    private final RestaurantServiceabilityUseCase serviceability = mock(RestaurantServiceabilityUseCase.class);
    private final MenuItemInventoryUseCase inventory = mock(MenuItemInventoryUseCase.class);
    private final ObjectProvider<MenuItemInventoryUseCase> inventoryProvider = mock(ObjectProvider.class);

    @Test
    void checkoutUsesPostgresCatalogAsAuthorityAndIgnoresClientPriceAndName() {
        Restaurant restaurant = restaurant(RestaurantStatus.ACTIVE);
        MenuItem item = item(restaurant, "Canonical", "120.50");
        given(restaurant, item);

        var result = service().validateOrderFromOrderService(request("Fake", 1.0, 2));

        assertThat(result.getIsValid()).isTrue();
        assertThat(result.getCalculatedTotal()).isEqualTo(241.0);
        assertThat(result.getItemValidations()).singleElement().extracting("menuItemName").isEqualTo("Canonical");
    }

    @Test
    void pausedRestaurantFailsClosedEvenWhenMenuIsAvailable() {
        Restaurant restaurant = restaurant(RestaurantStatus.PAUSED);
        MenuItem item = item(restaurant, "Canonical", "120.50");
        given(restaurant, item);

        var result = service().validateOrderFromOrderService(request("Canonical", 120.5, 1));

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getErrors()).extracting("errorCode").contains("RESTAURANT_NOT_ACCEPTING_ORDERS");
    }

    @Test
    void archivedRestaurantFailsClosed() {
        Restaurant restaurant = restaurant(RestaurantStatus.ARCHIVED);
        MenuItem item = item(restaurant, "Canonical", "120.50");
        given(restaurant, item);

        var result = service().validateOrderFromOrderService(request("Canonical", 120.5, 1));

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getErrors()).extracting("errorCode").contains("RESTAURANT_NOT_ACCEPTING_ORDERS");
    }

    @Test
    void missingMenuRowFailsClosedInsteadOfTrustingCache() {
        Restaurant restaurant = restaurant(RestaurantStatus.ACTIVE);
        when(restaurants.findById(30L)).thenReturn(Optional.of(restaurant));
        when(items.findAllById(List.of(9L))).thenReturn(List.of());

        var result = service().validateOrderFromOrderService(request("Canonical", 120.5, 1));

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getErrors()).extracting("errorCode").contains("MENU_ITEM_NOT_AVAILABLE");
    }

    @Test
    void foreignMenuItemFailsClosed() {
        Restaurant restaurant = restaurant(RestaurantStatus.ACTIVE);
        Restaurant other = restaurant(RestaurantStatus.ACTIVE);
        other.setId(31L);
        MenuItem item = item(other, "Foreign", "120.50");
        given(restaurant, item);

        var result = service().validateOrderFromOrderService(request("Foreign", 120.5, 1));

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getErrors()).extracting("errorCode").contains("MENU_ITEM_NOT_AVAILABLE");
    }

    @Test
    void nonPositiveCanonicalPriceFailsClosed() {
        Restaurant restaurant = restaurant(RestaurantStatus.ACTIVE);
        MenuItem item = item(restaurant, "Broken", "0");
        given(restaurant, item);

        var result = service().validateOrderFromOrderService(request("Broken", 0.0, 1));

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getErrors()).extracting("errorCode").contains("MENU_ITEM_NOT_AVAILABLE");
    }

    @Test
    void invalidQuantityFailsClosedAndDoesNotCallInventory() {
        Restaurant restaurant = restaurant(RestaurantStatus.ACTIVE);
        MenuItem item = item(restaurant, "Canonical", "120.50");
        given(restaurant, item);

        var result = service().validateOrderFromOrderService(request("Canonical", 120.5, 0));

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getErrors()).extracting("errorCode").contains("MENU_ITEM_NOT_AVAILABLE");
    }

    private OrderCacheValidationServiceImpl service() {
        when(serviceability.evaluate(anyLong(), any(), any()))
                .thenReturn(new ServiceabilityDecision(false, false, null, null, "CAPABILITY_DISABLED"));
        when(inventoryProvider.getIfAvailable()).thenReturn(inventory);
        when(inventory.availability(anyLong(), anyLong(), anyInt()))
                .thenReturn(new InventoryAvailability(true, null));
        return new OrderCacheValidationServiceImpl(restaurants, items, serviceability,
                inventoryProvider, Clock.fixed(Instant.parse("2026-01-01T05:00:00Z"), ZoneOffset.UTC));
    }

    private void given(Restaurant restaurant, MenuItem item) {
        when(restaurants.findById(30L)).thenReturn(Optional.of(restaurant));
        when(items.findAllById(List.of(9L))).thenReturn(List.of(item));
    }

    private OrderValidationRequest request(String name, Double price, int quantity) {
        return OrderValidationRequest.builder().restaurantId(30L).items(List.of(
                OrderValidationRequest.OrderItemRequest.builder().menuItemId(9L)
                        .menuItemName(name).price(price).quantity(quantity).build())).build();
    }

    private Restaurant restaurant(RestaurantStatus status) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(30L);
        restaurant.setName("Canonical Restaurant");
        restaurant.setCreatorId(1L);
        restaurant.setLifecycleStatus(status);
        restaurant.setTimeZone("UTC");
        return restaurant;
    }

    private MenuItem item(Restaurant restaurant, String name, String price) {
        MenuItem item = new MenuItem();
        item.setId(9L);
        item.setRestaurant(restaurant);
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setStatus(MenuItem.Status.AVAILABLE);
        return item;
    }
}
