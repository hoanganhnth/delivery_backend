package com.delivery.settlement_service.service.impl;

import com.delivery.settlement_service.dto.response.BalanceResponse;
import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.exception.ResourceNotFoundException;
import com.delivery.settlement_service.mapper.BalanceMapper;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.BalanceService;
import com.delivery.settlement.application.api.ledger.BalanceAccountUseCase;
import com.delivery.settlement.application.ledger.DefaultBalanceAccountUseCase;
import com.delivery.settlement.application.ledger.LedgerResourceMissing;
import com.delivery.settlement.domain.ledger.LedgerOwner;
import com.delivery.settlement_service.adapter.JpaBalanceAccountAdapter;
import com.delivery.settlement_service.adapter.JpaLedgerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BalanceServiceImpl implements BalanceService {

    private final BalanceRepository balanceRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceMapper balanceMapper;

    private final BalanceAccountUseCase core;

    public BalanceServiceImpl(BalanceRepository balances, TransactionRepository transactions, BalanceMapper mapper) {
        this.balanceRepository = balances;
        this.transactionRepository = transactions;
        this.balanceMapper = mapper;
        this.core = new DefaultBalanceAccountUseCase(new JpaBalanceAccountAdapter(balances));
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Balance createBalance(Long entityId, EntityType entityType) {
        return ensureAccount(entityId, entityType);
    }

    @Override
    @Transactional
    public BalanceResponse getBalance(Long entityId, EntityType entityType) {
        return balanceMapper.toResponse(ensureAccount(entityId, entityType));
    }

    private Balance ensureAccount(Long entityId, EntityType entityType) {
        try {
            var account = core.ensureAccount(new LedgerOwner(entityId,
                    JpaLedgerAdapter.enumValue(entityType, com.delivery.settlement.domain.EntityType.class)));
            return balanceRepository.findById(account.id()).orElseThrow(() -> new ResourceNotFoundException("Balance", "entityId", entityId));
        } catch (LedgerResourceMissing error) {
            throw new ResourceNotFoundException(error.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceResponse> getAllBalances() {
        log.info("Getting all balances");
        return balanceRepository.findAll(PageRequest.of(0, BalanceAccountUseCase.COMPATIBILITY_LIST_LIMIT)).stream()
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
