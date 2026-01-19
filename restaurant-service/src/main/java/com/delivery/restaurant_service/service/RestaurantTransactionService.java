package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.response.RestaurantTransactionResponse;

import java.util.List;

public interface RestaurantTransactionService {

    /**
     * Get all transactions for a restaurant
     */
    List<RestaurantTransactionResponse> getTransactionsByRestaurantId(Long restaurantId);

    /**
     * Get withdrawal transactions only
     */
    List<RestaurantTransactionResponse> getWithdrawalsByRestaurantId(Long restaurantId);

    /**
     * Get single transaction by ID
     */
    RestaurantTransactionResponse getTransactionById(Long transactionId);
}
