package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantCacheServiceImplTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOperations;
    @Mock RestaurantRepository restaurantRepository;
    @Mock MenuItemRepository menuItemRepository;

    private RestaurantCacheServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RestaurantCacheServiceImpl(
                redisTemplate, restaurantRepository, menuItemRepository);
    }

    @Test
    void restaurantRedisFailureFallsBackToCanonicalDatabaseRow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(7L);
        restaurant.setCreatorId(11L);
        restaurant.setName("Quán canonical");
        restaurant.setAddressLat(10.77);
        restaurant.setAddressLng(106.70);

        when(valueOperations.get("catalog:restaurant:7"))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        when(restaurantRepository.findById(7L)).thenReturn(Optional.of(restaurant));

        Map<String, Object> result = service.getRestaurantFromCache(7L);

        assertEquals("Quán canonical", result.get("name"));
        assertEquals(11L, result.get("creatorId"));
        verify(restaurantRepository).findById(7L);
    }

    @Test
    void menuRedisFailureFallsBackToCanonicalDatabaseRow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(7L);
        MenuItem menuItem = new MenuItem();
        menuItem.setId(9L);
        menuItem.setRestaurant(restaurant);
        menuItem.setName("Cơm canonical");
        menuItem.setPrice(new BigDecimal("45000"));

        when(valueOperations.get("catalog:menu_item:9"))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        when(menuItemRepository.findById(9L)).thenReturn(Optional.of(menuItem));

        Map<String, Object> result = service.getMenuItemFromCache(9L);

        assertEquals("Cơm canonical", result.get("name"));
        assertEquals(new BigDecimal("45000"), result.get("price"));
        verify(menuItemRepository).findById(9L);
    }

    @Test
    void canonicalOperatingHourFieldsAllowOnlyTimeInsideConfiguredWindow() {
        Map<String, Object> restaurant = Map.of(
                "openingHour", "08:00",
                "closingHour", "22:00");

        assertTrue(RestaurantCacheServiceImpl.isWithinOperatingHours(
                restaurant, LocalTime.of(12, 0)));
        assertFalse(RestaurantCacheServiceImpl.isWithinOperatingHours(
                restaurant, LocalTime.of(7, 59)));
        assertFalse(RestaurantCacheServiceImpl.isWithinOperatingHours(
                restaurant, LocalTime.of(22, 1)));
    }

    @Test
    void missingOperatingHourPairKeepsExistingAlwaysOpenPolicy() {
        assertTrue(RestaurantCacheServiceImpl.isWithinOperatingHours(
                Map.of(), LocalTime.NOON));
    }

    @Test
    void onlyCanonicalAvailableMenuStatusCanBeOrdered() {
        assertTrue(RestaurantCacheServiceImpl.isAvailableMenuItemStatus(
                MenuItem.Status.AVAILABLE));
        assertFalse(RestaurantCacheServiceImpl.isAvailableMenuItemStatus(
                MenuItem.Status.SOLD_OUT));
        assertFalse(RestaurantCacheServiceImpl.isAvailableMenuItemStatus(
                MenuItem.Status.DISCONTINUED));
        assertFalse(RestaurantCacheServiceImpl.isAvailableMenuItemStatus(null));
    }
}
