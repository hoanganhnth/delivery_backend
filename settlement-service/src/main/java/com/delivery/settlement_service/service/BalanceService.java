package com.delivery.settlement_service.service;

import com.delivery.settlement_service.dto.response.BalanceResponse;
import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;

import java.math.BigDecimal;
import java.util.List;

public interface BalanceService {

    /**
     * Create initial balance for an entity (all zeros)
     */
    Balance createBalance(Long entityId, EntityType entityType);

    /**
     * Get balance for an entity
     */
    BalanceResponse getBalance(Long entityId, EntityType entityType);

    /**
     * Recalculate balance from transaction history
     * Use when balance is suspected to be incorrect
     */
    Balance recalculateBalance(Long entityId, EntityType entityType);

    /**
     * Get all balances (for admin)
     */
    List<BalanceResponse> getAllBalances();

    /**
     * Get total earnings for an entity (calculated from transactions, not stored)
     */
    BigDecimal getTotalEarnings(Long entityId, EntityType entityType);
}
