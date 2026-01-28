package com.delivery.settlement_service.service.impl;

import com.delivery.settlement_service.dto.response.BalanceResponse;
import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction;
import com.delivery.settlement_service.entity.Transaction.TransactionStatus;
import com.delivery.settlement_service.exception.ResourceNotFoundException;
import com.delivery.settlement_service.mapper.BalanceMapper;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.BalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceServiceImpl implements BalanceService {

    private final BalanceRepository balanceRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceMapper balanceMapper;

    @Override
    @Transactional
    public Balance createBalance(Long entityId, EntityType entityType) {
        log.info("Creating balance for entity: {} ({})", entityId, entityType);

        if (balanceRepository.existsByEntityIdAndEntityType(entityId, entityType)) {
            log.warn("Balance already exists for entity: {} ({})", entityId, entityType);
            return balanceRepository.findByEntityIdAndEntityType(entityId, entityType)
                    .orElseThrow(() -> new ResourceNotFoundException("Balance", "entityId", entityId));
        }

        Balance balance = Balance.builder()
                .entityId(entityId)
                .entityType(entityType)
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .holdingBalance(BigDecimal.ZERO)
                .build();

        Balance saved = balanceRepository.save(balance);
        log.info("✅ Created balance for entity: {} ({})", entityId, entityType);
        return saved;
    }

    @Override
    @Transactional
    public BalanceResponse getBalance(Long entityId, EntityType entityType) {
        log.info("Getting balance for entity: {} ({})", entityId, entityType);

        Balance balance = balanceRepository.findByEntityIdAndEntityType(entityId, entityType)
                .orElseGet(() -> createBalance(entityId, entityType));

        return balanceMapper.toResponse(balance);
    }

    @Override
    @Transactional
    public Balance recalculateBalance(Long entityId, EntityType entityType) {
        log.info("🔄 Recalculating balance from transactions for entity: {} ({})", entityId, entityType);

        Balance balance = balanceRepository.findByEntityIdAndEntityType(entityId, entityType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Balance not found for entity: " + entityId + " (" + entityType + ")"));

        // Get all completed transactions
        List<Transaction> transactions = transactionRepository
                .findByEntityIdAndEntityTypeAndStatus(entityId, entityType, TransactionStatus.COMPLETED);

        // Recalculate balances
        BigDecimal availableBalance = BigDecimal.ZERO;
        BigDecimal pendingBalance = BigDecimal.ZERO;
        BigDecimal holdingBalance = BigDecimal.ZERO;

        for (Transaction tx : transactions) {
            switch (tx.getReason()) {
                case WITHDRAW:
                    if (tx.getDirection() == Transaction.TransactionDirection.DEBIT) {
                        // Withdrawal moves money from available to pending
                        availableBalance = availableBalance.subtract(tx.getAmount());
                        pendingBalance = pendingBalance.add(tx.getAmount());
                    }
                    break;
                case HOLD:
                    if (tx.getDirection() == Transaction.TransactionDirection.DEBIT) {
                        // Hold moves money from available to holding
                        availableBalance = availableBalance.subtract(tx.getAmount());
                        holdingBalance = holdingBalance.add(tx.getAmount());
                    }
                    break;
                case RELEASE:
                    if (tx.getDirection() == Transaction.TransactionDirection.CREDIT) {
                        // Release moves money from holding back to available
                        holdingBalance = holdingBalance.subtract(tx.getAmount());
                        availableBalance = availableBalance.add(tx.getAmount());
                    }
                    break;
                default:
                    // Normal CREDIT/DEBIT
                    if (tx.getDirection() == Transaction.TransactionDirection.CREDIT) {
                        availableBalance = availableBalance.add(tx.getAmount());
                    } else {
                        availableBalance = availableBalance.subtract(tx.getAmount());
                    }
            }
        }

        // Update balance
        balance.setAvailableBalance(availableBalance);
        balance.setPendingBalance(pendingBalance);
        balance.setHoldingBalance(holdingBalance);

        Balance saved = balanceRepository.save(balance);
        log.info("✅ Recalculated balance for entity: {} ({}) - Available: {}, Pending: {}, Holding: {}",
                entityId, entityType, availableBalance, pendingBalance, holdingBalance);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceResponse> getAllBalances() {
        log.info("Getting all balances");
        return balanceRepository.findAll().stream()
                .map(balanceMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalEarnings(Long entityId, EntityType entityType) {
        log.info("Calculating total earnings for entity: {} ({})", entityId, entityType);
        return transactionRepository.calculateEntityTotalEarnings(entityId, entityType);
    }
}
