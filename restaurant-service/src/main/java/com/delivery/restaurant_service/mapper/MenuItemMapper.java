package com.delivery.restaurant_service.mapper;

import com.delivery.restaurant.application.api.MenuItemCreateResult;
import com.delivery.restaurant.application.api.MenuItemPageSlice;
import com.delivery.restaurant.application.api.MenuItemSnapshot;
import com.delivery.restaurant.application.api.MenuItemUpdateResult;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.entity.MenuItem;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class MenuItemMapper {

    public MenuItemResponse toResponse(MenuItem item) {
        if (item == null) {
            return null;
        }
        MenuItemResponse response = new MenuItemResponse();
        response.setId(item.getId());
        response.setRestaurantId(item.getRestaurant() == null ? null : item.getRestaurant().getId());
        response.setName(item.getName());
        response.setDescription(item.getDescription());
        response.setPrice(item.getPrice());
        response.setStatus(item.getStatus() == null ? null : item.getStatus().name());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());
        response.setImage(item.getImage());
        response.setVersion(item.getVersion());
        return response;
    }

    public MenuItemResponse toResponse(MenuItemSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        MenuItemResponse response = new MenuItemResponse();
        response.setId(snapshot.id());
        response.setRestaurantId(snapshot.restaurantId());
        response.setName(snapshot.name());
        response.setDescription(snapshot.description());
        response.setPrice(snapshot.price());
        response.setStatus(snapshot.status() == null ? null : snapshot.status().name());
        response.setCreatedAt(snapshot.createdAt());
        response.setUpdatedAt(snapshot.updatedAt());
        response.setImage(snapshot.image());
        response.setVersion(snapshot.version());
        return response;
    }

    public MenuItemResponse toResponse(MenuItemCreateResult result) {
        return result == null ? null : toResponse(result.snapshot());
    }

    public MenuItemResponse toResponse(MenuItemUpdateResult result) {
        return result == null ? null : toResponse(result.snapshot());
    }

    public Page<MenuItemResponse> toPage(MenuItemPageSlice source) {
        if (source == null) {
            return Page.empty();
        }
        List<MenuItemResponse> items = source.items().stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(items, PageRequest.of(source.page(), source.size()), source.totalItems());
    }
}
