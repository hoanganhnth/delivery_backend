package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.ApiPathConstants;
import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.dto.request.WithdrawalRequest;
import com.delivery.restaurant_service.dto.response.RestaurantBalanceResponse;
import com.delivery.restaurant_service.dto.response.RestaurantTransactionResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.RestaurantBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPathConstants.BALANCES)
@RequiredArgsConstructor
public class RestaurantBalanceController {

    private final RestaurantBalanceService restaurantBalanceService;

    /**
     * Get balance for a restaurant
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<BaseResponse<RestaurantBalanceResponse>> getBalanceByRestaurantId(
            @PathVariable Long restaurantId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId) {

        RestaurantBalanceResponse response = restaurantBalanceService.getBalanceByRestaurantId(restaurantId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    /**
     * Request withdrawal
     */
    @PostMapping("/restaurant/{restaurantId}/withdraw")
    public ResponseEntity<BaseResponse<RestaurantTransactionResponse>> requestWithdrawal(
            @PathVariable Long restaurantId,
            @RequestBody WithdrawalRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        RestaurantTransactionResponse response = restaurantBalanceService.requestWithdrawal(
                restaurantId,
                request.getAmount(),
                userId);

        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }
}
