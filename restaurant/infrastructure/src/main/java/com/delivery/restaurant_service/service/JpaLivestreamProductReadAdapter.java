package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.LivestreamProductReadPort;
import com.delivery.restaurant.application.api.LivestreamProductSnapshot;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaLivestreamProductReadAdapter implements LivestreamProductReadPort {
    private final MenuItemRepository menu;

    public JpaLivestreamProductReadAdapter(MenuItemRepository menu) {
        this.menu = menu;
    }

    @Override
    public Optional<LivestreamProductSnapshot> findProduct(Long productId) {
        return menu.findById(productId).map(item -> {
            var restaurant = item.getRestaurant();
            return new LivestreamProductSnapshot(item.getId(), restaurant == null ? null : restaurant.getId(),
                    item.getName(), item.getImage(), restaurant == null ? null : restaurant.getName(),
                    item.getStatus() == null ? null : MenuItemStatus.valueOf(item.getStatus().name()));
        });
    }
}
