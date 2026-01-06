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
import com.delivery.restaurant_service.service.RestaurantCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuItemServiceImpl implements MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final MenuItemMapper menuItemMapper;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantCacheService restaurantCacheService;

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
        if (!restaurant.getCreatorId().equals(creatorId) && !role.equalsIgnoreCase(RoleConstants.ADMIN)) {
            throw new AccessDeniedException("Creator does not have permission to add items to this restaurant");
        }
        
        MenuItem item = menuItemMapper.toEntity(request);
        item.setRestaurant(restaurant);
        MenuItem saved = menuItemRepository.save(item);
        
        // 🔥 Cache menu item after creation
        try {
            restaurantCacheService.cacheMenuItem(saved);
            log.info("✅ Cached new menu item: {} (ID: {}) for restaurant: {}", 
                saved.getName(), saved.getId(), restaurant.getName());
        } catch (Exception e) {
            log.warn("⚠️ Failed to cache menu item after creation: {}", e.getMessage());
        }
        
        return menuItemMapper.toResponse(saved);
    }

    @Override
    public MenuItemResponse updateMenuItem(Long id, UpdateMenuItemRequest request, Long creatorId) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found"));
        checkPermission(item, creatorId);
        
        menuItemMapper.updateEntityFromDto(request, item);
        MenuItem updated = menuItemRepository.save(item);
        
        // 🔥 Update cache after modification
        try {
            restaurantCacheService.cacheMenuItem(updated);
            log.info("🔄 Updated cache for menu item: {} (ID: {})", updated.getName(), updated.getId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to update cache after menu item update: {}", e.getMessage());
        }
        
        return menuItemMapper.toResponse(updated);
    }

    @Override
    public void deleteMenuItem(Long id, Long creatorId) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found"));

        checkPermission(item, creatorId);

        // 🔥 Remove from cache before deletion
        try {
            restaurantCacheService.removeMenuItemFromCache(id);
            log.info("🗑️ Removed menu item from cache: {} (ID: {})", item.getName(), id);
        } catch (Exception e) {
            log.warn("⚠️ Failed to remove menu item from cache: {}", e.getMessage());
        }

        menuItemRepository.delete(item);
    }

    @Override
    public List<MenuItemResponse> getItemsByRestaurant(Long restaurantId) {
        return menuItemRepository.findByRestaurantId(restaurantId).stream().map(menuItemMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MenuItemResponse> getAvailableItems(Long restaurantId) {
        return menuItemRepository.findByRestaurantIdAndStatus(restaurantId, MenuItem.Status.AVAILABLE).stream()
                .map(menuItemMapper::toResponse).collect(Collectors.toList());
    }
    
    @Override
    public List<MenuItemResponse> getMenuItemsByCreatorId(Long creatorId) {
        List<MenuItem> menuItems = menuItemRepository.findByRestaurantCreatorId(creatorId);
        return menuItems.stream()
                .map(menuItemMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Update menu item availability và cache
     */
    public void updateMenuItemAvailability(Long menuItemId, boolean isAvailable, Long creatorId) {
        MenuItem item = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found"));
        
        checkPermission(item, creatorId);
        
        // Update status in database
        MenuItem.Status newStatus = isAvailable ? MenuItem.Status.AVAILABLE : MenuItem.Status.SOLD_OUT;
        item.setStatus(newStatus);
        menuItemRepository.save(item);
        
        // 🔥 Update cache
        try {
            restaurantCacheService.updateMenuItemAvailability(menuItemId, isAvailable);
            log.info("🔄 Updated availability for menu item: {} -> {}", item.getName(), isAvailable);
        } catch (Exception e) {
            log.warn("⚠️ Failed to update menu item availability in cache: {}", e.getMessage());
        }
    }

    private void checkPermission(MenuItem item, Long creatorId) {
        if (!item.getRestaurant().getCreatorId().equals(creatorId)) {
            throw new AccessDeniedException("Creator does not have permission to modify this menu item");
        }
    }
}
