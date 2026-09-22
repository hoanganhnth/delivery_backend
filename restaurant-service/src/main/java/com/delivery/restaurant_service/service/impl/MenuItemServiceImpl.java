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
import com.delivery.restaurant_service.service.CatalogCacheSynchronizer;
import com.delivery.restaurant_service.service.SearchSyncPublisher;
import com.delivery.restaurant_service.service.ownership.ManagementAccess;
import com.delivery.restaurant_service.service.ownership.RestaurantOwnershipPolicy;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuItemServiceImpl implements MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final MenuItemMapper menuItemMapper;
    private final RestaurantRepository restaurantRepository;
    private final CatalogCacheSynchronizer cacheSynchronizer;
    private final SearchSyncPublisher searchSyncPublisher;
    private final RestaurantOwnershipPolicy restaurantOwnershipPolicy;

    @Override
    @Transactional(readOnly = true)
    public Page<MenuItemResponse> getManagedItemsPage(Long restaurantId, Long principalId,
            Long legacyUserId, String role, int page, int size) {
        requireManagementActor(principalId, legacyUserId, role);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page or size");
        var pageable = PageRequest.of(page, size);
        if (restaurantId != null) {
            var restaurant = restaurantRepository.findById(restaurantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
            restaurantOwnershipPolicy.assertCanManage(restaurant, principalId, legacyUserId, role);
            return menuItemRepository.findPageByRestaurantId(restaurantId, pageable).map(menuItemMapper::toResponse);
        }
        if (RoleConstants.ADMIN.equalsIgnoreCase(role)) {
            return menuItemRepository.findAll(pageable).map(menuItemMapper::toResponse);
        }
        return menuItemRepository.findManagedByOwner(principalId, legacyUserId,
                !restaurantOwnershipPolicy.isPrincipalOwnershipEnforced(), pageable).map(menuItemMapper::toResponse);
    }

    @Override
    @Transactional
    public MenuItemResponse createMenuItem(CreateMenuItemRequest request, Long creatorId, String role) {
        return createMenuItem(request, creatorId, creatorId, role);
    }

    @Override
    @Transactional
    public MenuItemResponse createMenuItem(CreateMenuItemRequest request, Long principalId,
                                           Long legacyUserId, String role) {
        requireManagementActor(principalId, legacyUserId, role);
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        authorizeWrite(restaurant, principalId, legacyUserId, role);
        
        MenuItem item = menuItemMapper.toEntity(request);
        item.setRestaurant(restaurant);
        MenuItem saved = menuItemRepository.save(item);
        
        cacheSynchronizer.cacheMenuItemAfterCommit(saved);
        
        // 🔥 Publish sync event for search service
        searchSyncPublisher.publishDishChange(saved, "CREATE");
        
        return menuItemMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MenuItemResponse updateMenuItem(Long id, UpdateMenuItemRequest request, Long creatorId, String role) {
        return updateMenuItem(id, request, creatorId, creatorId, role);
    }

    @Override
    @Transactional
    public MenuItemResponse updateMenuItem(Long id, UpdateMenuItemRequest request, Long principalId,
                                           Long legacyUserId, String role) {
        requireManagementActor(principalId, legacyUserId, role);
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found"));
        authorizeWrite(item.getRestaurant(), principalId, legacyUserId, role);
        
        menuItemMapper.updateEntityFromDto(request, item);
        MenuItem updated = menuItemRepository.save(item);
        
        cacheSynchronizer.cacheMenuItemAfterCommit(updated);
        
        // 🔥 Publish sync event for search service
        searchSyncPublisher.publishDishChange(updated, "UPDATE");
        
        return menuItemMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteMenuItem(Long id, Long creatorId, String role) {
        deleteMenuItem(id, creatorId, creatorId, role);
    }

    @Override
    @Transactional
    public void deleteMenuItem(Long id, Long principalId, Long legacyUserId, String role) {
        requireManagementActor(principalId, legacyUserId, role);
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found"));
        authorizeWrite(item.getRestaurant(), principalId, legacyUserId, role);

        if (item.getStatus() == MenuItem.Status.ARCHIVED) {
            return;
        }

        item.setStatus(MenuItem.Status.ARCHIVED);
        MenuItem archived = menuItemRepository.save(item);

        cacheSynchronizer.removeMenuItemAfterCommit(id);

        searchSyncPublisher.publishDishChange(archived, "DELETE");
    }

    @Override
    public List<MenuItemResponse> getItemsByRestaurant(Long restaurantId) {
        return menuItemRepository.findByRestaurantId(restaurantId, PageRequest.of(0, 100)).stream()
                .map(menuItemMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MenuItemResponse> getAvailableItems(Long restaurantId) {
        return menuItemRepository.findByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
                        restaurantId, MenuItem.Status.AVAILABLE, RestaurantStatus.ARCHIVED,
                        PageRequest.of(0, 100)).stream()
                .map(menuItemMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<MenuItemResponse> getManagedItemsByRestaurant(Long restaurantId, Long principalId,
                                                                Long legacyUserId, String role) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        restaurantOwnershipPolicy.assertCanManage(restaurant, principalId, legacyUserId, role);
        return getItemsByRestaurant(restaurantId);
    }
    
    @Override
    public List<MenuItemResponse> getMenuItemsByCreatorId(Long creatorId) {
        List<MenuItem> menuItems = menuItemRepository.findByRestaurantCreatorId(
                creatorId, PageRequest.of(0, 100));
        return menuItems.stream()
                .map(menuItemMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<MenuItemResponse> getItemsByRestaurantPage(Long restaurantId, int page, int size, boolean available) {
        Page<MenuItem> source = available
                ? menuItemRepository.findPageByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
                        restaurantId, MenuItem.Status.AVAILABLE, RestaurantStatus.ARCHIVED,
                        PageRequest.of(page, size))
                : menuItemRepository.findPageByRestaurantId(restaurantId, PageRequest.of(page, size));
        return source.map(menuItemMapper::toResponse);
    }

    @Override
    public Page<MenuItemResponse> getMenuItemsByCreatorPage(Long creatorId, int page, int size) {
        return menuItemRepository.findPageByRestaurantCreatorId(creatorId, PageRequest.of(page, size))
                .map(menuItemMapper::toResponse);
    }

    @Override
    public List<MenuItemResponse> getAllItems() {
        return menuItemRepository.findAll(PageRequest.of(0, 100)).stream()
                .map(menuItemMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<MenuItemResponse> getAllItemsPage(int page, int size) {
        return menuItemRepository.findAll(PageRequest.of(page, size))
                .map(menuItemMapper::toResponse);
    }
    
    private void authorizeWrite(Restaurant restaurant, Long principalId, Long legacyUserId, String role) {
        ManagementAccess access = restaurantOwnershipPolicy.assertCanManage(
                restaurant, principalId, legacyUserId, role);
        if (access.usedLegacyFallback() && principalId != null) {
            restaurant.setOwnerPrincipalId(principalId);
        }
    }

    private void requireManagementActor(Long principalId, Long legacyUserId, String role) {
        if (role == null || !RoleConstants.ALLOWED_CREATORS.contains(role.toUpperCase())
                || principalId == null || principalId <= 0) {
            throw new AccessDeniedException("Only authenticated ADMIN or SHOP_OWNER can manage menu items");
        }
    }
}
