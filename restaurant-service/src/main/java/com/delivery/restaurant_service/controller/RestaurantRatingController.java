package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.dto.request.RestaurantRatingRequest;
import com.delivery.restaurant_service.dto.response.RestaurantRatingResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.RestaurantRatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantRatingController {

    private final RestaurantRatingService ratingService;

    @PostMapping("/{restaurantId}/ratings")
    public ResponseEntity<BaseResponse<RestaurantRatingResponse>> submitRating(
            @PathVariable Long restaurantId,
            @RequestHeader(HttpHeaderConstants.X_USER_ID) Long customerId,
            @RequestBody RestaurantRatingRequest request) {
        
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
            @RequestHeader(HttpHeaderConstants.X_USER_ID) Long customerId) {
        List<RestaurantRatingResponse> responses = ratingService.getMyRatings(customerId);
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }

    @GetMapping("/admin/ratings")
    public ResponseEntity<BaseResponse<List<RestaurantRatingResponse>>> getAllRatings() {
        List<RestaurantRatingResponse> responses = ratingService.getAllRatings();
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }

    @PutMapping("/admin/ratings/{id}/status")
    public ResponseEntity<BaseResponse<RestaurantRatingResponse>> updateRatingStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        RestaurantRatingResponse response = ratingService.updateRatingStatus(id, status);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật trạng thái đánh giá thành công"));
    }
}
