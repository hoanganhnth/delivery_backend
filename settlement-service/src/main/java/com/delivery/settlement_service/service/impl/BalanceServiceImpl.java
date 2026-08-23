package com.delivery.settlement_service.service.impl;

import com.delivery.settlement_service.dto.response.BalanceResponse;
import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.exception.ResourceNotFoundException;
import com.delivery.settlement_service.mapper.BalanceMapper;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.BalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
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
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Balance createBalance(Long entityId, EntityType entityType) {
        log.info("Creating balance for entity: {} ({})", entityId, entityType);

        Balance existing = balanceRepository.findByEntityIdAndEntityType(entityId, entityType)
                .orElse(null);
        if (existing != null) {
            log.warn("Balance already exists for entity: {} ({})", entityId, entityType);
            return existing;
        }

        try {
            Balance balance = Balance.builder()
                    .entityId(entityId)
                    .entityType(entityType)
                    .availableBalance(BigDecimal.ZERO)
                    .depositBalance(BigDecimal.ZERO)
                    .reservedDepositBalance(BigDecimal.ZERO)
                    .pendingBalance(BigDecimal.ZERO)
                    .holdingBalance(BigDecimal.ZERO)
                    .totalDeposited(BigDecimal.ZERO)
                    .totalCodCollected(BigDecimal.ZERO)
                    .build();

            Balance saved = balanceRepository.saveAndFlush(balance);
            log.info("✅ Created balance for entity: {} ({})", entityId, entityType);
            return saved;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Balance already created concurrently for entity: {} ({})", entityId, entityType);
            return balanceRepository.findByEntityIdAndEntityType(entityId, entityType)
                    .orElseThrow(() -> new ResourceNotFoundException("Balance", "entityId", entityId));
        }
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
    @Transactional(readOnly = true)
    public List<BalanceResponse> getAllBalances() {
        log.info("Getting all balances");
        return balanceRepository.findAll(PageRequest.of(0, 100)).stream()
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
