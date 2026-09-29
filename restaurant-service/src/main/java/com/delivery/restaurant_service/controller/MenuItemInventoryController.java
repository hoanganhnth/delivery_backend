package com.delivery.restaurant_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.restaurant.application.api.MenuItemInventoryUseCase;
import com.delivery.restaurant.application.api.UpdateMenuItemInventoryCommand;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemInventoryRequest;
import com.delivery.restaurant_service.dto.response.MenuItemInventoryResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu-items/{menuItemId}/inventory")
public class MenuItemInventoryController {

    private final ObjectProvider<MenuItemInventoryUseCase> serviceProvider;

    @Value("${app.restaurant.inventory-enabled:false}")
    private boolean inventoryEnabled;

    public MenuItemInventoryController(ObjectProvider<MenuItemInventoryUseCase> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @GetMapping
    public ResponseEntity<BaseResponse<MenuItemInventoryResponse>> get(
            @PathVariable Long menuItemId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdminOrOwner(actor);
        MenuItemInventoryUseCase service = availableService();
        return ResponseEntity.ok(new BaseResponse<>(1,
                MenuItemInventoryResponse.from(service.getInventory(menuItemId))));
    }

    @PutMapping
    public ResponseEntity<BaseResponse<MenuItemInventoryResponse>> update(
            @PathVariable Long menuItemId,
            @Valid @RequestBody UpdateMenuItemInventoryRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdminOrOwner(actor);
        RestaurantActorRole role = actor.isAdmin()
                ? RestaurantActorRole.ADMIN : RestaurantActorRole.SHOP_OWNER;
        return ResponseEntity.ok(new BaseResponse<>(1, MenuItemInventoryResponse.from(
                availableService().updateInventory(menuItemId,
                        new UpdateMenuItemInventoryCommand(request.getOnHandQuantity(),
                                request.getExpectedRevision(), actor.getUserId(), role)))));
    }

    private void requireAdminOrOwner(AuthenticatedActor actor) {
        if (actor == null || actor.getUserId() == null
                || (!actor.isAdmin() && !actor.isShopOwner())) {
            throw new AccessDeniedException("Only ADMIN or SHOP_OWNER may access inventory");
        }
    }

    private MenuItemInventoryUseCase availableService() {
        if (!inventoryEnabled) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Inventory capability is disabled");
        }
        MenuItemInventoryUseCase service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Inventory capability is unavailable");
        }
        return service;
    }
}
