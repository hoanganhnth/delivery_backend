package com.delivery.settlement_service.service.impl;

import com.delivery.settlement.application.ledger.LedgerResourceMissing;
import com.delivery.settlement.application.api.ledger.LedgerUseCase;
import com.delivery.settlement.application.ledger.DefaultLedgerUseCase;
import com.delivery.settlement.domain.ledger.*;
import com.delivery.settlement_service.adapter.JpaLedgerAdapter;
import com.delivery.settlement_service.dto.request.RejectWithdrawalRequest;
import com.delivery.settlement_service.dto.response.TransactionResponse;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.TransactionStatus;
import com.delivery.settlement_service.entity.Transaction.WalletType;
import com.delivery.settlement_service.exception.InsufficientBalanceException;
import com.delivery.settlement_service.exception.ResourceNotFoundException;
import com.delivery.settlement_service.mapper.TransactionMapper;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.BalanceService;
import com.delivery.settlement_service.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import static com.delivery.settlement_service.adapter.JpaLedgerAdapter.enumValue;

/** Transaction/API mapping facade. Actual financial workflows run in LedgerUseCase. */
@Service
@Slf4j
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final JpaLedgerAdapter adapter;
    private final LedgerUseCase core;

    public TransactionServiceImpl(TransactionRepository transactions, BalanceRepository balances,
            BalanceService balanceService, TransactionMapper mapper) {
        this.transactionRepository = transactions;
        this.transactionMapper = mapper;
        this.adapter = new JpaLedgerAdapter(transactions, balances, balanceService);
        this.core = new DefaultLedgerUseCase(adapter, Clock.systemDefaultZone());
    }
    @Override @Transactional
    public Transaction createTransaction(Long entityId, EntityType entityType, Long orderId,
            TransactionDirection direction, TransactionReason reason, BigDecimal amount, String description) {
        return createTransaction(entityId, entityType, orderId, direction, reason, amount, description, WalletType.EARNINGS);
    }
    @Override @Transactional
    public Transaction createTransaction(Long entityId, EntityType entityType, Long orderId,
            TransactionDirection direction, TransactionReason reason, BigDecimal amount, String description, WalletType wallet) {
        return execute(() -> core.create(new LedgerPosting(entityId, enumValue(entityType, com.delivery.settlement.domain.EntityType.class),
                orderId, enumValue(direction, LedgerPosting.Direction.class), enumValue(reason, LedgerPosting.Reason.class),
                amount, description, enumValue(wallet, LedgerPosting.Wallet.class))));
    }
    @Override @Transactional
    public Transaction topUpDeposit(Long entityId, EntityType type, BigDecimal amount, String method) {
        return execute(() -> core.topUp(owner(entityId, type), amount, method));
    }
    @Override @Transactional(readOnly = true)
    public boolean checkCodEligibility(Long shipperId, BigDecimal amount) { return core.checkCodEligibility(shipperId, amount); }
    @Override @Transactional
    public Transaction requestWithdrawal(Long entityId, EntityType type, BigDecimal amount, Long requestedBy) {
        return execute(() -> core.requestWithdrawal(owner(entityId, type), amount));
    }
    @Override @Transactional
    public Transaction approveWithdrawal(Long id, Long adminId) { return execute(() -> core.approveWithdrawal(id, adminId)); }
    @Override @Transactional
    public Transaction rejectWithdrawal(Long id, Long adminId, RejectWithdrawalRequest request) {
        return execute(() -> core.rejectWithdrawal(id, adminId, request == null ? null : request.getReason()));
    }
    @Override @Transactional
    public Transaction reverseTransaction(Long id, Long adminId, String reason) { return execute(() -> core.reverse(id, adminId, reason)); }
    @Override @Transactional
    public Transaction holdBalance(Long entityId, BigDecimal amount, String description) { return execute(() -> core.hold(entityId, amount, description)); }
    @Override @Transactional
    public Transaction releaseBalance(Long entityId, BigDecimal amount, String description) { return execute(() -> core.release(entityId, amount, description)); }
    private LedgerOwner owner(Long id, EntityType type) {
        return new LedgerOwner(id, enumValue(type, com.delivery.settlement.domain.EntityType.class));
    }
    private Transaction execute(Supplier<LedgerEntry> operation) {
        try { return adapter.requireEntity(operation.get().id()); }
        catch (InsufficientWalletFunds error) { throw new InsufficientBalanceException(error.getMessage()); }
        catch (LedgerResourceMissing error) { throw new ResourceNotFoundException(error.getMessage()); }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions(Long entityId, EntityType entityType) {
        log.info("Getting transactions for entity: {} ({})", entityId, entityType);
        return transactionRepository.findByEntityIdAndEntityTypeOrderByCreatedAtDesc(
                        entityId, entityType, PageRequest.of(0, LedgerUseCase.COMPATIBILITY_LIST_LIMIT))
                .stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getPendingWithdrawals() {
        log.info("Getting pending withdrawals");
        return transactionRepository.findByStatusAndReasonOrderByCreatedAtDesc(
                        TransactionStatus.PENDING, TransactionReason.WITHDRAW,
                        PageRequest.of(0, LedgerUseCase.COMPATIBILITY_LIST_LIMIT))
                .stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions() {
        log.info("Getting all transactions");
        return transactionRepository.findAllByOrderByCreatedAtDesc(
                        PageRequest.of(0, LedgerUseCase.COMPATIBILITY_LIST_LIMIT))
                .stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
    }

}
