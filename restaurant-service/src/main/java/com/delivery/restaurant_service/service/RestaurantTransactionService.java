package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.entity.RestaurantTransaction;

import java.util.List;

public interface RestaurantTransactionService {
    // crud transaction
    // create transaction
    RestaurantTransaction createTransaction();
    // update transaction
    void updateTransaction(Long transactionId, Long restaurantId, Long orderId, Double amount);
    // delete transaction
    void deleteTransaction(Long transactionId);
    // get transaction by id
    RestaurantTransaction getTransactionById(Long transactionId);
    // get all transactions for a restaurant
    List<RestaurantTransaction> getAllTransactionsByRestaurantId(Long restaurantId);
}
