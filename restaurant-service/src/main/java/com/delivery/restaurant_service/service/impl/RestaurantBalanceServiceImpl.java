package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.dto.response.RestaurantBalanceResponse;
import com.delivery.restaurant_service.dto.response.RestaurantTransactionResponse;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.RestaurantBalance;
import com.delivery.restaurant_service.entity.RestaurantTransaction;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.mapper.RestaurantBalanceMapper;
import com.delivery.restaurant_service.mapper.RestaurantTransactionMapper;
import com.delivery.restaurant_service.repository.RestaurantBalanceRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.repository.RestaurantTransactionRepository;
import com.delivery.restaurant_service.service.RestaurantBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantBalanceServiceImpl implements RestaurantBalanceService {

    private final RestaurantBalanceRepository restaurantBalanceRepository;
    private final RestaurantTransactionRepository restaurantTransactionRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantBalanceMapper restaurantBalanceMapper;
    private final RestaurantTransactionMapper restaurantTransactionMapper;

    @Override
    @Transactional
    public RestaurantBalance createInitialBalance(Restaurant restaurant) {
        log.info("Creating initial balance for restaurant: {} (ID: {})", restaurant.getName(), restaurant.getId());

        // Check if balance already exists
        if (restaurantBalanceRepository.existsByRestaurantId(restaurant.getId())) {
            log.warn("Balance already exists for restaurant ID: {}", restaurant.getId());
            return restaurantBalanceRepository.findByRestaurantId(restaurant.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Balance not found"));
        }

        RestaurantBalance balance = RestaurantBalance.builder()
                .restaurant(restaurant)
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalEarnings(BigDecimal.ZERO)
                .build();

        RestaurantBalance saved = restaurantBalanceRepository.save(balance);
        log.info("✅ Created initial balance for restaurant ID: {}", restaurant.getId());

        return saved;
    }

    @Override
    public RestaurantBalanceResponse getBalanceByRestaurantId(Long restaurantId) {
        log.info("Getting balance for restaurant ID: {}", restaurantId);

        RestaurantBalance balance = restaurantBalanceRepository.findByRestaurantId(restaurantId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Balance not found for restaurant ID: " + restaurantId));

        return restaurantBalanceMapper.toResponse(balance);
    }

    @Override
    @Transactional
    public void earnFromOrder(Long restaurantId, Long orderId, BigDecimal amount, String description) {
        log.info("💰 Adding earnings for restaurant ID: {}, order ID: {}, amount: {}",
                restaurantId, orderId, amount);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Invalid amount: {}", amount);
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        // Get restaurant
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));

        // Get or create balance
        RestaurantBalance balance = restaurantBalanceRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> createInitialBalance(restaurant));

        // Update balance
        balance.setAvailableBalance(balance.getAvailableBalance().add(amount));
        balance.setTotalEarnings(balance.getTotalEarnings().add(amount));
        restaurantBalanceRepository.save(balance);

        // Create transaction record
        RestaurantTransaction transaction = RestaurantTransaction.builder()
                .restaurant(restaurant)
                .orderId(orderId)
                .type(RestaurantTransaction.TypeTransaction.EARNING)
                .amount(amount)
                .description(description != null ? description : "Earnings from order #" + orderId)
                .status(RestaurantTransaction.TransactionStatus.COMPLETED)
                .build();

        restaurantTransactionRepository.save(transaction);

        log.info("✅ Successfully added earnings to restaurant ID: {}, new available balance: {}",
                restaurantId, balance.getAvailableBalance());
    }

    @Override
    @Transactional
    public RestaurantTransactionResponse requestWithdrawal(Long restaurantId, BigDecimal amount, Long requestedBy) {
        log.info("💸 Processing withdrawal request for restaurant ID: {}, amount: {}, requested by: {}",
                restaurantId, amount, requestedBy);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Invalid withdrawal amount: {}", amount);
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero");
        }

        // Get restaurant
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));

        // Get balance
        RestaurantBalance balance = restaurantBalanceRepository.findByRestaurantId(restaurantId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Balance not found for restaurant ID: " + restaurantId));

        // Check if sufficient balance
        if (balance.getAvailableBalance().compareTo(amount) < 0) {
            log.error("Insufficient balance. Available: {}, Requested: {}",
                    balance.getAvailableBalance(), amount);
            throw new IllegalArgumentException("Insufficient balance for withdrawal");
        }

        // Update balance
        balance.setAvailableBalance(balance.getAvailableBalance().subtract(amount));
        balance.setPendingBalance(balance.getPendingBalance().add(amount));
        restaurantBalanceRepository.save(balance);

        // Create withdrawal transaction
        RestaurantTransaction transaction = RestaurantTransaction.builder()
                .restaurant(restaurant)
                .type(RestaurantTransaction.TypeTransaction.WITHDRAW)
                .amount(amount)
                .description("Withdrawal request")
                .status(RestaurantTransaction.TransactionStatus.PENDING)
                .build();

        RestaurantTransaction saved = restaurantTransactionRepository.save(transaction);

        log.info("✅ Withdrawal request created. Transaction ID: {}, new available balance: {}, pending balance: {}",
                saved.getId(), balance.getAvailableBalance(), balance.getPendingBalance());

        return restaurantTransactionMapper.toResponse(saved);
    }
}
