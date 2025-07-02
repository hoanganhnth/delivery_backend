package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;

import java.util.List;

public interface MenuItemService {

    MenuItemResponse createMenuItem(CreateMenuItemRequest request,
                                    Long creatorId,
                                    String role);

    MenuItemResponse updateMenuItem(Long id, UpdateMenuItemRequest request, Long creatorId);

    void deleteMenuItem(Long id, Long creatorId);

    List<MenuItemResponse> getItemsByRestaurant(Long restaurantId);

    List<MenuItemResponse> getAvailableItems(Long restaurantId);
}
