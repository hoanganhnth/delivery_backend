package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.RestaurantRatingRequest;
import com.delivery.restaurant_service.dto.response.RestaurantRatingResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.RestaurantRatingService;
import com.delivery.restaurant_service.client.OrderEligibilityClient;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import com.delivery.restaurant_service.payload.PageResponse;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantRatingController {

    private final RestaurantRatingService ratingService;
    private final OrderEligibilityClient orderEligibilityClient;

    @PostMapping("/{restaurantId}/ratings")
    public ResponseEntity<BaseResponse<RestaurantRatingResponse>> submitRating(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedActor actor,
            @Valid @RequestBody RestaurantRatingRequest request) {
        requireCustomerRole(actor);
        orderEligibilityClient.requireDeliveredOrder(request.getOrderId(), actor.getUserId(), restaurantId);
        RestaurantRatingResponse response = ratingService.submitRating(restaurantId, actor.getUserId(), request);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Đánh giá nhà hàng thành công"));
    }

    @GetMapping("/{restaurantId}/ratings")
    public ResponseEntity<BaseResponse<List<RestaurantRatingResponse>>> getRestaurantRatings(@PathVariable Long restaurantId) {
        List<RestaurantRatingResponse> responses = ratingService.getRestaurantRatings(restaurantId);
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }

    @GetMapping("/{restaurantId}/ratings/page")
    public ResponseEntity<BaseResponse<PageResponse<RestaurantRatingResponse>>> getRestaurantRatingsPage(
            @PathVariable Long restaurantId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(
                ratingService.getRestaurantRatingsPage(restaurantId, page, size))));
    }

    @GetMapping("/me/ratings")
    public ResponseEntity<BaseResponse<List<RestaurantRatingResponse>>> getMyRatings(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireCustomerRole(actor);
        List<RestaurantRatingResponse> responses = ratingService.getMyRatings(actor.getUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }

    @GetMapping("/admin/ratings")
    public ResponseEntity<BaseResponse<List<RestaurantRatingResponse>>> getAllRatings(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (actor == null || !actor.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Chỉ ADMIN được xem tất cả đánh giá"));
        }
        List<RestaurantRatingResponse> responses = ratingService.getAllRatings();
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }

    @GetMapping("/admin/ratings/page")
    public ResponseEntity<BaseResponse<PageResponse<RestaurantRatingResponse>>> getAllRatingsPage(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (actor == null || !actor.isAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new BaseResponse<>(0, null, "Chỉ ADMIN được xem tất cả đánh giá"));
        validatePage(page, size);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(ratingService.getAllRatingsPage(page, size))));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page or size");
    }

    @PutMapping("/admin/ratings/{id}/status")
    public ResponseEntity<BaseResponse<RestaurantRatingResponse>> updateRatingStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (actor == null || !actor.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Chỉ ADMIN được duyệt đánh giá"));
        }
        RestaurantRatingResponse response = ratingService.updateRatingStatus(id, status);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật trạng thái đánh giá thành công"));
    }

    private void requireCustomerRole(AuthenticatedActor actor) {
        if (actor == null || !actor.isUser()) {
            throw new AccessDeniedException("Only USER can access customer ratings");
        }
    }
}
