package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.response.RestaurantBalanceResponse;
import com.delivery.restaurant_service.dto.response.RestaurantTransactionResponse;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.RestaurantBalance;

import java.math.BigDecimal;

public interface RestaurantBalanceService {

    /**
     * Create initial balance when restaurant is created
     */
    RestaurantBalance createInitialBalance(Restaurant restaurant);

    /**
     * Get balance for a restaurant
     */
    RestaurantBalanceResponse getBalanceByRestaurantId(Long restaurantId);

    /**
     * Add earnings from completed delivery
     */
    void earnFromOrder(Long restaurantId, Long orderId, BigDecimal amount, String description);

    /**
     * Request withdrawal
     */
    RestaurantTransactionResponse requestWithdrawal(Long restaurantId, BigDecimal amount, Long requestedBy);
}
