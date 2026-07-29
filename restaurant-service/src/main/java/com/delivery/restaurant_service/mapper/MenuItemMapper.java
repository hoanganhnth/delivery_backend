package com.delivery.restaurant_service.mapper;

import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.entity.MenuItem;
import org.springframework.stereotype.Component;

@Component
public class MenuItemMapper {

    public MenuItem toEntity(CreateMenuItemRequest request) {
        if (request == null) {
            return null;
        }
        MenuItem item = new MenuItem();
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setImage(request.getImage());
        return item;
    }

    public void updateEntityFromDto(UpdateMenuItemRequest request, MenuItem item) {
        if (request == null || item == null) {
            return;
        }
        if (request.getName() != null) item.setName(request.getName());
        if (request.getDescription() != null) item.setDescription(request.getDescription());
        if (request.getPrice() != null) item.setPrice(request.getPrice());
        if (request.getStatus() != null) item.setStatus(request.getStatus());
        if (request.getImage() != null) item.setImage(request.getImage());
    }

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
        return response;
    }
}
