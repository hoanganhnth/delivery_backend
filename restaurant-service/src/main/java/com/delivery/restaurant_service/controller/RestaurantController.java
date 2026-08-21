package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.ApiPathConstants;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.RestaurantService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.RESTAURANTS)
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @PostMapping
    public ResponseEntity<BaseResponse<RestaurantResponse>> create(
            @Valid @RequestBody CreateRestaurantRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        RestaurantResponse response = restaurantService.createRestaurant(
                request, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<RestaurantResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRestaurantRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        RestaurantResponse response = restaurantService.updateRestaurant(
                id, request, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        restaurantService.deleteRestaurant(id, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<RestaurantResponse>> getById(@PathVariable Long id) {
        RestaurantResponse response = restaurantService.getRestaurantById(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<RestaurantResponse>>> getAll() {
        List<RestaurantResponse> list = restaurantService.getAllRestaurants();
        return ResponseEntity.ok(new BaseResponse<>(1, list));
    }

    @GetMapping("/search")
    public ResponseEntity<BaseResponse<List<RestaurantResponse>>> search(@RequestParam String keyword) {
        List<RestaurantResponse> list = restaurantService.findByName(keyword);
        return ResponseEntity.ok(new BaseResponse<>(1, list));
    }
    
    @GetMapping("/my-restaurants")
    public ResponseEntity<BaseResponse<List<RestaurantResponse>>> getMyRestaurants(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        
        if (actor == null || actor.getPrincipalId() == null || actor.getLegacyUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (!actor.isShopOwner()) {
            throw new AccessDeniedException("Only SHOP_OWNER can view owned restaurants");
        }
        
        List<RestaurantResponse> list = restaurantService.getRestaurantsByOwnerPrincipalId(
                actor.getPrincipalId(), actor.getLegacyUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, list));
    }

    private void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.getPrincipalId() == null || actor.getLegacyUserId() == null) {
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
