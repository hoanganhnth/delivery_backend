package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.ApiPathConstants;
import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.dto.response.RestaurantTransactionResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.RestaurantTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.TRANSACTIONS)
@RequiredArgsConstructor
public class TransactionController {

    private final RestaurantTransactionService restaurantTransactionService;

    /**
     * Get all transactions for a restaurant
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<BaseResponse<List<RestaurantTransactionResponse>>> getTransactionsByRestaurantId(
            @PathVariable Long restaurantId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId) {

        List<RestaurantTransactionResponse> transactions = restaurantTransactionService
                .getTransactionsByRestaurantId(restaurantId);

        return ResponseEntity.ok(new BaseResponse<>(1, transactions));
    }

    /**
     * Get withdrawal history for a restaurant
     */
    @GetMapping("/restaurant/{restaurantId}/withdrawals")
    public ResponseEntity<BaseResponse<List<RestaurantTransactionResponse>>> getWithdrawalsByRestaurantId(
            @PathVariable Long restaurantId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId) {

        List<RestaurantTransactionResponse> withdrawals = restaurantTransactionService
                .getWithdrawalsByRestaurantId(restaurantId);

        return ResponseEntity.ok(new BaseResponse<>(1, withdrawals));
    }

    /**
     * Get single transaction by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<RestaurantTransactionResponse>> getTransactionById(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId) {

        RestaurantTransactionResponse transaction = restaurantTransactionService.getTransactionById(id);
        return ResponseEntity.ok(new BaseResponse<>(1, transaction));
    }
}
