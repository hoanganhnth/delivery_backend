package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant.application.api.RestaurantOwnershipLookupUseCase;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.application.api.RestaurantOrderDecisionUseCase;
import com.delivery.restaurant_service.dto.request.ConfirmRestaurantOrderRequest;
import com.delivery.restaurant_service.dto.request.RejectRestaurantOrderRequest;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/restaurants/orders")
@RequiredArgsConstructor
public class RestaurantOrderController {

    private final RestaurantOrderDecisionUseCase eventPublisher;
    private final RestaurantOwnershipLookupUseCase ownership;

    @Value("${app.identity.principal-ownership.enforced:false}")
    private boolean principalOwnershipEnforced;

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<BaseResponse<String>> confirmOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody ConfirmRestaurantOrderRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {

        ResponseEntity<BaseResponse<String>> denied = requireOwnerOrAdmin(actor);
        if (denied != null) return denied;
        if (orderId == null || orderId <= 0) {
            return ResponseEntity.badRequest().body(new BaseResponse<>(0, null, "orderId phải là số dương"));
        }

        Long restaurantId = request.getRestaurantId();
        Integer estimatedPrepTime = request.getEstimatedPrepTime();
        String notes = request.getNotes();

        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(new BaseResponse<>(0, null, "restaurantId là bắt buộc"));
        }
        if (!canManageRestaurant(restaurantId, actor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Bạn không sở hữu nhà hàng này"));
        }
        if (estimatedPrepTime == null || estimatedPrepTime <= 0 || estimatedPrepTime > 240) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "estimatedPrepTime phải từ 1 đến 240 phút"));
        }

        eventPublisher.confirm(orderId, restaurantId, actor.getUserId(), estimatedPrepTime, notes);
        return ResponseEntity.ok(new BaseResponse<>(1, "CONFIRMED", "Đã xác nhận đơn hàng"));
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<BaseResponse<String>> rejectOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody RejectRestaurantOrderRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {

        ResponseEntity<BaseResponse<String>> denied = requireOwnerOrAdmin(actor);
        if (denied != null) return denied;
        if (orderId == null || orderId <= 0) {
            return ResponseEntity.badRequest().body(new BaseResponse<>(0, null, "orderId phải là số dương"));
        }

        Long restaurantId = request.getRestaurantId();
        String reason = request.getReason();

        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(new BaseResponse<>(0, null, "restaurantId là bắt buộc"));
        }
        if (!canManageRestaurant(restaurantId, actor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Bạn không sở hữu nhà hàng này"));
        }
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(new BaseResponse<>(0, null, "Lý do từ chối là bắt buộc"));
        }

        eventPublisher.reject(orderId, restaurantId, actor.getUserId(), reason);
        return ResponseEntity.ok(new BaseResponse<>(1, "REJECTED", "Đã từ chối đơn hàng"));
    }

    private ResponseEntity<BaseResponse<String>> requireOwnerOrAdmin(AuthenticatedActor actor) {
        if (actor == null || (!actor.isShopOwner() && !actor.isAdmin())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Chỉ chủ nhà hàng hoặc admin được thao tác"));
        }
        return null;
    }

    private boolean canManageRestaurant(Long restaurantId, AuthenticatedActor actor) {
        if (actor == null) return false;
        return ownership.canDecideOrder(restaurantId,
                actor.isAdmin() ? RestaurantActorRole.ADMIN : actor.isShopOwner() ? RestaurantActorRole.SHOP_OWNER : RestaurantActorRole.OTHER,
                actor.getPrincipalId(), actor.getLegacyUserId(), principalOwnershipEnforced);
    }
}
