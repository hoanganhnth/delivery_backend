package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.RestaurantRatingRequest;
import com.delivery.restaurant_service.dto.response.RestaurantRatingResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant.application.api.RestaurantRatingPage;
import com.delivery.restaurant.application.api.RestaurantRatingResult;
import com.delivery.restaurant.application.api.RestaurantRatingUseCase;
import com.delivery.restaurant.application.api.SubmitRestaurantRatingCommand;
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

    private final RestaurantRatingUseCase ratingService;

    @PostMapping("/{restaurantId}/ratings")
    public ResponseEntity<BaseResponse<RestaurantRatingResponse>> submitRating(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedActor actor,
            @Valid @RequestBody RestaurantRatingRequest request) {
        requireCustomerRole(actor);
        RestaurantRatingResponse response = toResponse(ratingService.submitRating(
                new SubmitRestaurantRatingCommand(
                        restaurantId, actor.getUserId(), request.getOrderId(), request.getRating(), request.getComment())));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Đánh giá nhà hàng thành công"));
    }

    @GetMapping("/{restaurantId}/ratings")
    public ResponseEntity<BaseResponse<List<RestaurantRatingResponse>>> getRestaurantRatings(@PathVariable Long restaurantId) {
        List<RestaurantRatingResponse> responses = ratingService.getRestaurantRatings(restaurantId).stream()
                .map(RestaurantRatingController::toResponse)
                .toList();
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }

    @GetMapping("/{restaurantId}/ratings/page")
    public ResponseEntity<BaseResponse<PageResponse<RestaurantRatingResponse>>> getRestaurantRatingsPage(
            @PathVariable Long restaurantId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return ResponseEntity.ok(new BaseResponse<>(1, toPageResponse(
                ratingService.getRestaurantRatingsPage(restaurantId, page, size))));
    }

    @GetMapping("/me/ratings")
    public ResponseEntity<BaseResponse<List<RestaurantRatingResponse>>> getMyRatings(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireCustomerRole(actor);
        List<RestaurantRatingResponse> responses = ratingService.getMyRatings(actor.getUserId()).stream()
                .map(RestaurantRatingController::toResponse)
                .toList();
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }

    @GetMapping("/admin/ratings")
    public ResponseEntity<BaseResponse<List<RestaurantRatingResponse>>> getAllRatings(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (actor == null || !actor.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Chỉ ADMIN được xem tất cả đánh giá"));
        }
        List<RestaurantRatingResponse> responses = ratingService.getAllRatings().stream()
                .map(RestaurantRatingController::toResponse)
                .toList();
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }

    @GetMapping("/admin/ratings/page")
    public ResponseEntity<BaseResponse<PageResponse<RestaurantRatingResponse>>> getAllRatingsPage(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (actor == null || !actor.isAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new BaseResponse<>(0, null, "Chỉ ADMIN được xem tất cả đánh giá"));
        validatePage(page, size);
        return ResponseEntity.ok(new BaseResponse<>(1, toPageResponse(ratingService.getAllRatingsPage(page, size))));
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
        RestaurantRatingResponse response = toResponse(ratingService.updateRatingStatus(id, status));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật trạng thái đánh giá thành công"));
    }

    private void requireCustomerRole(AuthenticatedActor actor) {
        if (actor == null || !actor.isUser()) {
            throw new AccessDeniedException("Only USER can access customer ratings");
        }
    }

    private static RestaurantRatingResponse toResponse(RestaurantRatingResult result) {
        RestaurantRatingResponse response = new RestaurantRatingResponse();
        response.setId(result.id());
        response.setRestaurantId(result.restaurantId());
        response.setCustomerId(result.customerId());
        response.setOrderId(result.orderId());
        response.setRating(result.rating());
        response.setComment(result.comment());
        response.setStatus(result.status());
        response.setCreatedAt(result.createdAt());
        return response;
    }

    private static PageResponse<RestaurantRatingResponse> toPageResponse(RestaurantRatingPage page) {
        return new PageResponse<>(page.items().stream().map(RestaurantRatingController::toResponse).toList(),
                page.page(), page.size(), page.totalItems(), page.totalPages(), page.hasNext());
    }
}
