package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.entity.RestaurantTransaction;
import com.delivery.restaurant_service.mapper.RestaurantTransactionMapper;
import com.delivery.restaurant_service.service.RestaurantTransactionService;

import java.util.List;

public class RestaurantTransactionServiceImpl implements RestaurantTransactionService {

    private final RestaurantTransactionService restaurantTransactionService;

    private final RestaurantTransactionMapper restaurantTransactionMapper;

    public RestaurantTransactionServiceImpl(RestaurantTransactionService restaurantTransactionService,
                                            RestaurantTransactionMapper restaurantTransactionMapper) {
        this.restaurantTransactionService = restaurantTransactionService;
        this.restaurantTransactionMapper = restaurantTransactionMapper;
    }

    @Override
    public RestaurantTransaction createTransaction() {
        return null;
    }

    @Override
    public void updateTransaction(Long transactionId, Long restaurantId, Long orderId, Double amount) {

    }

    @Override
    public void deleteTransaction(Long transactionId) {

    }

    @Override
    public RestaurantTransaction getTransactionById(Long transactionId) {
        return null;
    }

    @Override
    public List<RestaurantTransaction> getAllTransactionsByRestaurantId(Long restaurantId) {
        return null;
    }
}
