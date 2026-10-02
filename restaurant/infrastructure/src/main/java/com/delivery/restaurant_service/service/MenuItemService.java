package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.MenuItemLifecycleRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;

import java.util.List;
import org.springframework.data.domain.Page;

public interface MenuItemService {
    Page<MenuItemResponse> getManagedItemsPage(Long restaurantId, Long principalId,
            Long legacyUserId, String role, int page, int size);


    MenuItemResponse createMenuItem(CreateMenuItemRequest request,
                                    Long principalId,
                                    Long legacyUserId,
                                    String role);

    MenuItemResponse updateMenuItem(Long id, UpdateMenuItemRequest request,
                                    Long principalId, Long legacyUserId, String role);

    void deleteMenuItem(Long id, Long principalId, Long legacyUserId, String role);

    MenuItemResponse changeLifecycle(Long id, MenuItemLifecycleRequest request,
                                     Long principalId, Long legacyUserId, String role);

    List<MenuItemResponse> getAvailableItems(Long restaurantId);

    Page<MenuItemResponse> getAvailableItemsPage(Long restaurantId, int page, int size);
}
