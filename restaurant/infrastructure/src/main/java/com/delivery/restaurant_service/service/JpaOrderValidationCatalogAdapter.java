package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaOrderValidationCatalogAdapter implements OrderValidationCatalogPort {
    private final RestaurantRepository restaurants;
    private final MenuItemRepository items;
    public JpaOrderValidationCatalogAdapter(RestaurantRepository restaurants, MenuItemRepository items) {
        this.restaurants = restaurants;
        this.items = items;
    }
    @Override public Optional<RestaurantSnapshot> findRestaurant(Long id) {
        return restaurants.findById(id).map(JpaRestaurantReadAdapter::snapshot);
    }
    @Override public List<MenuItemSnapshot> findItems(List<Long> ids) {
        return items.findAllById(ids).stream().map(JpaMenuItemCreationAdapter::snapshot).toList();
    }
}
