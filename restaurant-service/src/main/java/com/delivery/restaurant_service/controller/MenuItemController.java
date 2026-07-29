package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.ApiPathConstants;
import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.MenuItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
    public ResponseEntity<BaseResponse<MenuItemResponse>> create(@Valid @RequestBody CreateMenuItemRequest request,
                                                                 @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long creatorId,
                                                                 @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        MenuItemResponse response = menuItemService.createMenuItem(request, creatorId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<MenuItemResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMenuItemRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID,required = false) Long creatorId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        MenuItemResponse response = menuItemService.updateMenuItem(id, request, creatorId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable Long id,
                                                     @RequestHeader(value = HttpHeaderConstants.X_USER_ID,required = false) Long creatorId,
                                                     @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        menuItemService.deleteMenuItem(id, creatorId, role);
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
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long creatorId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        
        if (creatorId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (!RoleConstants.OWNER.equals(role)) {
            throw new AccessDeniedException("Only SHOP_OWNER can view owned menu items");
        }
        
        List<MenuItemResponse> list = menuItemService.getMenuItemsByCreatorId(creatorId);
        return ResponseEntity.ok(new BaseResponse<>(1, list));
    }
}
