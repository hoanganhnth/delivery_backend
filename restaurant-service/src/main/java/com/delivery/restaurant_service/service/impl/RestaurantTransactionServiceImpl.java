package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.dto.response.RestaurantTransactionResponse;
import com.delivery.restaurant_service.entity.RestaurantTransaction;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.mapper.RestaurantTransactionMapper;
import com.delivery.restaurant_service.repository.RestaurantTransactionRepository;
import com.delivery.restaurant_service.service.RestaurantTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantTransactionServiceImpl implements RestaurantTransactionService {

    private final RestaurantTransactionRepository restaurantTransactionRepository;
    private final RestaurantTransactionMapper restaurantTransactionMapper;

    @Override
    public List<RestaurantTransactionResponse> getTransactionsByRestaurantId(Long restaurantId) {
        log.info("Getting all transactions for restaurant ID: {}", restaurantId);

        List<RestaurantTransaction> transactions = restaurantTransactionRepository
                .findByRestaurant_IdOrderByCreatedAtDesc(restaurantId);

        return transactions.stream()
                .map(restaurantTransactionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestaurantTransactionResponse> getWithdrawalsByRestaurantId(Long restaurantId) {
        log.info("Getting withdrawal transactions for restaurant ID: {}", restaurantId);

        List<RestaurantTransaction> withdrawals = restaurantTransactionRepository
                .findByRestaurant_IdAndTypeOrderByCreatedAtDesc(
                        restaurantId,
                        RestaurantTransaction.TypeTransaction.WITHDRAW);

        return withdrawals.stream()
                .map(restaurantTransactionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public RestaurantTransactionResponse getTransactionById(Long transactionId) {
        log.info("Getting transaction by ID: {}", transactionId);

        RestaurantTransaction transaction = restaurantTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        return restaurantTransactionMapper.toResponse(transaction);
    }
}
