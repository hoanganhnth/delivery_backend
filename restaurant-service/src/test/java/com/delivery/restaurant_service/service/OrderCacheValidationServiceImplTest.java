package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.service.impl.OrderCacheValidationServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderCacheValidationServiceImplTest {

    private final RestaurantCacheService cache = mock(RestaurantCacheService.class);
    private final OrderCacheValidationServiceImpl service = new OrderCacheValidationServiceImpl(cache);

    @Test
    void missingRestaurantReturnsNullFactsInsteadOfPlaceholders() {
        OrderValidationRequest request = request(30L);
        when(cache.getRestaurantFromCache(30L)).thenReturn(null);

        var result = service.validateOrderFromOrderService(request);

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getRestaurantInfo().getRestaurantName()).isNull();
        assertThat(result.getRestaurantInfo().getOperatingHours()).isNull();
    }

    @Test
    void cachedRestaurantWithoutCanonicalNameFailsClosed() {
        OrderValidationRequest request = request(30L);
        Map<String, Object> restaurant = new HashMap<>();
        restaurant.put("address", "123 Street");
        when(cache.getRestaurantFromCache(30L)).thenReturn(restaurant);
        when(cache.isRestaurantAvailable(30L)).thenReturn(true);

        var result = service.validateOrderFromOrderService(request);

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getRestaurantInfo().getRestaurantName()).isNull();
        assertThat(result.getRestaurantInfo().getIsAvailable()).isFalse();
        assertThat(result.getErrors()).extracting("errorCode")
                .contains("RESTAURANT_NAME_MISSING");
    }

    @Test
    void discontinuedMenuItemFailsClosedAtInternalOrderValidationBoundary() {
        Map<String, Object> restaurant = new HashMap<>();
        restaurant.put("name", "Quán canonical");
        when(cache.getRestaurantFromCache(30L)).thenReturn(restaurant);
        when(cache.isRestaurantAvailable(30L)).thenReturn(true);
        when(cache.getMenuItemFromCache(9L)).thenReturn(Map.of(
                "restaurantId", 30L,
                "name", "Món đã ngừng bán",
                "price", 45000,
                "status", "DISCONTINUED"));

        OrderValidationRequest request = OrderValidationRequest.builder()
                .restaurantId(30L)
                .items(List.of(OrderValidationRequest.OrderItemRequest.builder()
                        .menuItemId(9L)
                        .quantity(1)
                        .build()))
                .build();

        var result = service.validateOrderFromOrderService(request);

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getItemValidations()).singleElement()
                .extracting("isAvailable").isEqualTo(false);
        assertThat(result.getErrors()).extracting("errorCode")
                .contains("MENU_ITEM_NOT_AVAILABLE");
    }

    @Test
    void menuItemMissingRestaurantIdentityFailsClosed() {
        stubCanonicalRestaurant();
        when(cache.getMenuItemFromCache(9L)).thenReturn(Map.of(
                "name", "Món không rõ owner",
                "price", 45000,
                "status", "AVAILABLE"));

        var result = service.validateOrderFromOrderService(requestWithItem(9L));

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getErrors()).extracting("errorCode")
                .contains("MENU_ITEM_NOT_AVAILABLE");
    }

    @Test
    void menuItemWithNonPositiveCanonicalPriceFailsClosed() {
        stubCanonicalRestaurant();
        when(cache.getMenuItemFromCache(9L)).thenReturn(Map.of(
                "restaurantId", 30L,
                "name", "Món giá lỗi",
                "price", 0,
                "status", "AVAILABLE"));

        var result = service.validateOrderFromOrderService(requestWithItem(9L));

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getItemValidations()).singleElement()
                .extracting("priceMatches").isEqualTo(false);
        assertThat(result.getErrors()).extracting("errorCode")
                .contains("MENU_ITEM_NOT_AVAILABLE");
    }

    private void stubCanonicalRestaurant() {
        when(cache.getRestaurantFromCache(30L)).thenReturn(Map.of("name", "Quán canonical"));
        when(cache.isRestaurantAvailable(30L)).thenReturn(true);
    }

    private OrderValidationRequest requestWithItem(Long menuItemId) {
        return OrderValidationRequest.builder()
                .restaurantId(30L)
                .items(List.of(OrderValidationRequest.OrderItemRequest.builder()
                        .menuItemId(menuItemId)
                        .quantity(1)
                        .build()))
                .build();
    }

    private OrderValidationRequest request(Long restaurantId) {
        return OrderValidationRequest.builder()
                .restaurantId(restaurantId)
                .items(List.of())
                .build();
    }
}
