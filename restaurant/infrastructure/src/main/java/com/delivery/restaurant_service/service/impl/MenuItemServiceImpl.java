package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant.application.api.CreateMenuItemCommand;
import com.delivery.restaurant.application.api.CreateMenuItemUseCase;
import com.delivery.restaurant.application.api.MenuItemManagementQuery;
import com.delivery.restaurant.application.api.MenuItemManagementReadUseCase;
import com.delivery.restaurant.application.api.MenuItemPageSlice;
import com.delivery.restaurant.application.api.MenuItemReadUseCase;
import com.delivery.restaurant.application.api.UpdateMenuItemCommand;
import com.delivery.restaurant.application.api.UpdateMenuItemUseCase;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.MenuItemLifecycleRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.mapper.MenuItemMapper;
import com.delivery.restaurant_service.service.MenuItemService;
import com.delivery.restaurant_service.service.ownership.RestaurantOwnershipPolicy;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MenuItemServiceImpl implements MenuItemService {

    private final MenuItemMapper menuItemMapper;
    private final RestaurantOwnershipPolicy restaurantOwnershipPolicy;
    private final CatalogLifecycleService catalogLifecycleService;
    private final CreateMenuItemUseCase createMenuItemUseCase;
    private final UpdateMenuItemUseCase updateMenuItemUseCase;
    private final MenuItemReadUseCase menuItemReadUseCase;
    private final MenuItemManagementReadUseCase menuItemManagementReadUseCase;

    @Override
    public Page<MenuItemResponse> getManagedItemsPage(Long restaurantId, Long principalId,
            Long legacyUserId, String role, int page, int size) {
        MenuItemManagementQuery query = new MenuItemManagementQuery(
                restaurantId, principalId, managementLegacyId(principalId, legacyUserId, role),
                actorRole(role), restaurantOwnershipPolicy.isPrincipalOwnershipEnforced(), page, size);
        try {
            MenuItemPageSlice result = menuItemManagementReadUseCase.read(query)
                    .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
            return menuItemMapper.toPage(result);
        } catch (ManagementAccessException ex) {
            throw accessDenied();
        }
    }

    @Override
    public MenuItemResponse createMenuItem(CreateMenuItemRequest request, Long principalId,
            Long legacyUserId, String role) {
        CreateMenuItemCommand command = new CreateMenuItemCommand(
                request.getRestaurantId(), principalId,
                managementLegacyId(principalId, legacyUserId, role), actorRole(role),
                restaurantOwnershipPolicy.isPrincipalOwnershipEnforced(), request.getName(),
                request.getDescription(), request.getPrice(), request.getImage());
        try {
            return menuItemMapper.toResponse(createMenuItemUseCase.create(command)
                    .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found")));
        } catch (ManagementAccessException ex) {
            throw accessDenied();
        }
    }

    @Override
    public MenuItemResponse updateMenuItem(Long id, UpdateMenuItemRequest request, Long principalId,
            Long legacyUserId, String role) {
        UpdateMenuItemCommand command = new UpdateMenuItemCommand(
                id, principalId, managementLegacyId(principalId, legacyUserId, role), actorRole(role),
                restaurantOwnershipPolicy.isPrincipalOwnershipEnforced(), request.getName(),
                request.getDescription(), request.getPrice(), menuItemStatus(request.getStatus()),
                request.getImage(), request.getRestaurantId());
        try {
            return menuItemMapper.toResponse(updateMenuItemUseCase.update(command)
                    .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found")));
        } catch (ManagementAccessException ex) {
            throw accessDenied();
        }
    }

    @Override
    public void deleteMenuItem(Long id, Long principalId, Long legacyUserId, String role) {
        catalogLifecycleService.archiveMenuItem(id, principalId, legacyUserId, role);
    }

    @Override
    public MenuItemResponse changeLifecycle(Long id, MenuItemLifecycleRequest request,
            Long principalId, Long legacyUserId, String role) {
        return catalogLifecycleService.changeMenuItemLifecycle(
                id, request, principalId, legacyUserId, role);
    }

    @Override
    public List<MenuItemResponse> getAvailableItems(Long restaurantId) {
        return menuItemReadUseCase.listPublic(restaurantId).stream()
                .map(menuItemMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<MenuItemResponse> getAvailableItemsPage(Long restaurantId, int page, int size) {
        return menuItemMapper.toPage(menuItemReadUseCase.pagePublic(restaurantId, page, size));
    }

    private RestaurantActorRole actorRole(String role) {
        if (RoleConstants.ADMIN.equalsIgnoreCase(role)) return RestaurantActorRole.ADMIN;
        if (RoleConstants.OWNER.equalsIgnoreCase(role)) return RestaurantActorRole.SHOP_OWNER;
        return RestaurantActorRole.OTHER;
    }

    private Long managementLegacyId(Long principalId, Long legacyUserId, String role) {
        // ADMIN decisions do not consult legacy ownership, but the application API
        // still requires a complete trusted identity tuple for every management call.
        return legacyUserId != null || !RoleConstants.ADMIN.equalsIgnoreCase(role)
                ? legacyUserId : principalId;
    }

    private MenuItemStatus menuItemStatus(MenuItem.Status status) {
        return status == null ? null : MenuItemStatus.valueOf(status.name());
    }

    private AccessDeniedException accessDenied() {
        return new AccessDeniedException("Actor does not own this restaurant");
    }
}
