package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.ApiPathConstants;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.MenuItemService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.MENU_ITEMS)
public class MenuItemController {

    private final MenuItemService menuItemService;

    public MenuItemController(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }

    @PostMapping
    public ResponseEntity<BaseResponse<MenuItemResponse>> create(
            @Valid @RequestBody CreateMenuItemRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        MenuItemResponse response = menuItemService.createMenuItem(request, actor.getUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<MenuItemResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMenuItemRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        MenuItemResponse response = menuItemService.updateMenuItem(id, request, actor.getUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        menuItemService.deleteMenuItem(id, actor.getUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, null));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<BaseResponse<List<MenuItemResponse>>> getByRestaurant(@PathVariable Long restaurantId) {
        List<MenuItemResponse> list = menuItemService.getItemsByRestaurant(restaurantId);
        return ResponseEntity.ok(new BaseResponse<>(1, list));
    }

    @GetMapping("/restaurant/{restaurantId}/available")
    public ResponseEntity<BaseResponse<List<MenuItemResponse>>> getAvailableItems(@PathVariable Long restaurantId) {
        List<MenuItemResponse> list = menuItemService.getAvailableItems(restaurantId);
        return ResponseEntity.ok(new BaseResponse<>(1, list));
    }
    
    @GetMapping("/my-menu-items")
    public ResponseEntity<BaseResponse<List<MenuItemResponse>>> getMyMenuItems(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        
        if (actor == null || actor.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (!actor.isShopOwner()) {
            throw new AccessDeniedException("Only SHOP_OWNER can view owned menu items");
        }
        
        List<MenuItemResponse> list = menuItemService.getMenuItemsByCreatorId(actor.getUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, list));
    }

    private void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.getUserId() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập");
        }
    }

    private String getRoleString(AuthenticatedActor actor) {
        if (actor == null) return null;
        if (actor.isAdmin()) return RoleConstants.ADMIN;
        if (actor.isShopOwner()) return RoleConstants.OWNER;
        return RoleConstants.CUSTOMER;
    }
}
