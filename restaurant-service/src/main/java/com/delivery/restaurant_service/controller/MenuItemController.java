package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.ApiPathConstants;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.MenuItemLifecycleRequest;
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
import org.springframework.data.domain.Page;
import com.delivery.restaurant_service.payload.PageResponse;

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
        MenuItemResponse response = menuItemService.createMenuItem(
                request, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<MenuItemResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMenuItemRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        MenuItemResponse response = menuItemService.updateMenuItem(
                id, request, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        menuItemService.deleteMenuItem(id, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, null));
    }

    @PatchMapping("/{id}/lifecycle")
    public ResponseEntity<BaseResponse<MenuItemResponse>> changeLifecycle(
            @PathVariable Long id,
            @Valid @RequestBody MenuItemLifecycleRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        MenuItemResponse response = menuItemService.changeLifecycle(
                id, request, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<BaseResponse<List<MenuItemResponse>>> getByRestaurant(@PathVariable Long restaurantId) {
        List<MenuItemResponse> list = menuItemService.getAvailableItems(restaurantId);
        return ResponseEntity.ok(new BaseResponse<>(1, list));
    }

    @GetMapping("/restaurant/{restaurantId}/available")
    public ResponseEntity<BaseResponse<List<MenuItemResponse>>> getAvailableItems(@PathVariable Long restaurantId) {
        List<MenuItemResponse> list = menuItemService.getAvailableItems(restaurantId);
        return ResponseEntity.ok(new BaseResponse<>(1, list));
    }

    @GetMapping("/restaurant/{restaurantId}/page")
    public ResponseEntity<BaseResponse<PageResponse<MenuItemResponse>>> getPage(
            @PathVariable Long restaurantId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        validatePage(page, size);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(
                menuItemService.getItemsByRestaurantPage(restaurantId, page, size, true))));
    }

    @GetMapping("/restaurant/{restaurantId}/available/page")
    public ResponseEntity<BaseResponse<PageResponse<MenuItemResponse>>> getAvailablePage(
            @PathVariable Long restaurantId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        validatePage(page, size);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(
                menuItemService.getItemsByRestaurantPage(restaurantId, page, size, true))));
    }
    
    @GetMapping("/my-menu-items")
    public ResponseEntity<BaseResponse<List<MenuItemResponse>>> getMyMenuItems(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestParam(required = false) Long restaurantId) {
        
        requireActor(actor);
        if (!actor.isShopOwner() && !actor.isAdmin()) {
            throw new AccessDeniedException("Only SHOP_OWNER or ADMIN can view owned menu items");
        }
        
        List<MenuItemResponse> list = menuItemService.getManagedItemsPage(restaurantId,
                actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor), 0, 100).getContent();
        return ResponseEntity.ok(new BaseResponse<>(1, list));
    }

    @GetMapping("/my-menu-items/page")
    public ResponseEntity<BaseResponse<PageResponse<MenuItemResponse>>> getMyMenuItemsPage(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size) {
        if (actor == null || actor.getPrincipalId() == null || (!actor.isShopOwner() && !actor.isAdmin())) {
            throw new AccessDeniedException("Only SHOP_OWNER or ADMIN can view owned menu items");
        }
        validatePage(page, size);
        Page<MenuItemResponse> result = menuItemService.getManagedItemsPage(restaurantId,
                actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor), page, size);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(result)));
    }

    public ResponseEntity<BaseResponse<List<MenuItemResponse>>> getMyMenuItems(
            AuthenticatedActor actor) {
        return getMyMenuItems(actor, null);
    }

    public ResponseEntity<BaseResponse<PageResponse<MenuItemResponse>>> getMyMenuItemsPage(
            AuthenticatedActor actor, int page, int size) {
        return getMyMenuItemsPage(actor, null, page, size);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page or size");
    }

    private void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.getPrincipalId() == null) {
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
