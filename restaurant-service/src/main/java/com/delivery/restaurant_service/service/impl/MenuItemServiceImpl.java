package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.mapper.MenuItemMapper;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.MenuItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MenuItemServiceImpl implements MenuItemService {

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private MenuItemMapper menuItemMapper;

    @Autowired
    private RestaurantRepository restaurantRepository;


    @Override
    public MenuItemResponse createMenuItem(CreateMenuItemRequest request, Long creatorId, String role) {
        // Check if the creatorId matches the restaurant's creatorId


        if (role == null || !RoleConstants.ALLOWED_CREATORS.contains(role.toUpperCase())) {
            throw new AccessDeniedException("Only ADMIN or OWNER can create menu items");
        }
        if (creatorId == null) {
            throw new AccessDeniedException("You must be authenticated to create a menu item");
        }
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        MenuItem item = menuItemMapper.toEntity(request);
        item.setRestaurant(restaurant);
        MenuItem saved = menuItemRepository.save(item);
        return menuItemMapper.toResponse(saved);
    }

    @Override
    public MenuItemResponse updateMenuItem(Long id, UpdateMenuItemRequest request, Long creatorId) {
        MenuItem item = menuItemRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("MenuItem not found"));
        checkPermission(item, creatorId);
        menuItemMapper.updateEntityFromDto(request, item);
        MenuItem updated = menuItemRepository.save(item);
        return menuItemMapper.toResponse(updated);
    }

    @Override
    public void deleteMenuItem(Long id, Long creatorId) {
        MenuItem item = menuItemRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("MenuItem not found"));

        checkPermission(item, creatorId);

        menuItemRepository.delete(item);
    }

    @Override
    public List<MenuItemResponse> getItemsByRestaurant(Long restaurantId) {
        return menuItemRepository.findByRestaurantId(restaurantId).stream().map(menuItemMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<MenuItemResponse> getAvailableItems(Long restaurantId) {
        return menuItemRepository.findByRestaurantIdAndStatus(restaurantId, MenuItem.Status.AVAILABLE).stream().map(menuItemMapper::toResponse).collect(Collectors.toList());
    }

    private void checkPermission(MenuItem item, Long creatorId) {
        if (!item.getRestaurant().getCreatorId().equals(creatorId)) {
            throw new AccessDeniedException("Creator does not have permission to modify this menu item");
        }
    }
}
