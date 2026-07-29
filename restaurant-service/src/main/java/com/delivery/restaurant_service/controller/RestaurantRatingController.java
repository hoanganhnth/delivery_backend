package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.RestaurantRatingRequest;
import com.delivery.restaurant_service.dto.response.RestaurantRatingResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.RestaurantRatingService;
import com.delivery.restaurant_service.client.OrderEligibilityClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantRatingController {

    private final RestaurantRatingService ratingService;
    private final OrderEligibilityClient orderEligibilityClient;

    @PostMapping("/{restaurantId}/ratings")
    public ResponseEntity<BaseResponse<RestaurantRatingResponse>> submitRating(
            @PathVariable Long restaurantId,
            @RequestHeader(HttpHeaderConstants.X_USER_ID) Long customerId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role,
            @Valid @RequestBody RestaurantRatingRequest request) {
        requireCustomerRole(role);
        orderEligibilityClient.requireDeliveredOrder(request.getOrderId(), customerId, restaurantId);
        RestaurantRatingResponse response = ratingService.submitRating(restaurantId, customerId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Đánh giá nhà hàng thành công"));
    }

    @GetMapping("/{restaurantId}/ratings")
    public ResponseEntity<BaseResponse<List<RestaurantRatingResponse>>> getRestaurantRatings(@PathVariable Long restaurantId) {
        List<RestaurantRatingResponse> responses = ratingService.getRestaurantRatings(restaurantId);
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }

    @GetMapping("/me/ratings")
    public ResponseEntity<BaseResponse<List<RestaurantRatingResponse>>> getMyRatings(
            @RequestHeader(HttpHeaderConstants.X_USER_ID) Long customerId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        requireCustomerRole(role);
        List<RestaurantRatingResponse> responses = ratingService.getMyRatings(customerId);
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }

    @GetMapping("/admin/ratings")
    public ResponseEntity<BaseResponse<List<RestaurantRatingResponse>>> getAllRatings(
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        if (!RoleConstants.ADMIN.equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Chỉ ADMIN được xem tất cả đánh giá"));
        }
        List<RestaurantRatingResponse> responses = ratingService.getAllRatings();
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }

    @PutMapping("/admin/ratings/{id}/status")
    public ResponseEntity<BaseResponse<RestaurantRatingResponse>> updateRatingStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        if (!RoleConstants.ADMIN.equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Chỉ ADMIN được duyệt đánh giá"));
        }
        RestaurantRatingResponse response = ratingService.updateRatingStatus(id, status);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật trạng thái đánh giá thành công"));
    }

    private void requireCustomerRole(String role) {
        if (!RoleConstants.CUSTOMER.equals(role)) {
            throw new AccessDeniedException("Only USER can access customer ratings");
        }
    }
}
