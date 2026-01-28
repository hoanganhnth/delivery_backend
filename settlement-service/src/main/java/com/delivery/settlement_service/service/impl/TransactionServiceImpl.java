package com.delivery.settlement_service.service.impl;

import com.delivery.settlement_service.dto.request.RejectWithdrawalRequest;
import com.delivery.settlement_service.dto.response.TransactionResponse;
import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.TransactionStatus;
import com.delivery.settlement_service.exception.InsufficientBalanceException;
import com.delivery.settlement_service.exception.ResourceNotFoundException;
import com.delivery.settlement_service.mapper.TransactionMapper;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.BalanceService;
import com.delivery.settlement_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final BalanceRepository balanceRepository;
    private final BalanceService balanceService;
    private final TransactionMapper transactionMapper;

    @Override
    @Transactional
    public Transaction createTransaction(Long entityId, EntityType entityType, Long orderId,
                                        TransactionDirection direction, TransactionReason reason,
                                        BigDecimal amount, String description) {
        log.info("Creating transaction: entity={} ({}), direction={}, reason={}, amount={}",
                entityId, entityType, direction, reason, amount);

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        // Get or create balance
        Balance balance = balanceRepository.findByEntityIdAndEntityType(entityId, entityType)
                .orElseGet(() -> balanceService.createBalance(entityId, entityType));

        // Create transaction (append-only, immutable)
        Transaction transaction = Transaction.builder()
                .entityId(entityId)
                .entityType(entityType)
                .orderId(orderId)
                .direction(direction)
                .reason(reason)
                .amount(amount)
                .description(description)
                .status(TransactionStatus.COMPLETED)
                .build();

        Transaction saved = transactionRepository.save(transaction);

        // Update balance based on transaction
        updateBalanceFromTransaction(balance, saved);

        log.info("✅ Created transaction ID: {} for entity: {} ({})", saved.getId(), entityId, entityType);
        return saved;
    }

    @Override
    @Transactional
    public Transaction earnFromOrder(Long entityId, EntityType entityType, Long orderId,
                                    BigDecimal amount, String description) {
        TransactionReason reason = entityType == EntityType.RESTAURANT 
                ? TransactionReason.ORDER_EARNING 
                : TransactionReason.DELIVERY_FEE;

        return createTransaction(entityId, entityType, orderId,
                TransactionDirection.CREDIT, reason, amount, description);
    }

    @Override
    @Transactional
    public Transaction requestWithdrawal(Long entityId, EntityType entityType,
                                        BigDecimal amount, Long requestedBy) {
        log.info("Processing withdrawal request: entity={} ({}), amount={}", entityId, entityType, amount);

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero");
        }

        // Get balance
        Balance balance = balanceRepository.findByEntityIdAndEntityType(entityId, entityType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Balance not found for entity: " + entityId + " (" + entityType + ")"));

        // Check sufficient balance
        if (balance.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Available: %s, Requested: %s",
                            balance.getAvailableBalance(), amount));
        }

        // Create PENDING withdrawal transaction
        Transaction transaction = Transaction.builder()
                .entityId(entityId)
                .entityType(entityType)
                .direction(TransactionDirection.DEBIT)
                .reason(TransactionReason.WITHDRAW)
                .amount(amount)
                .description("Withdrawal request")
                .status(TransactionStatus.PENDING)
                .build();

        Transaction saved = transactionRepository.save(transaction);

        // Move money from available to pending
        balance.setAvailableBalance(balance.getAvailableBalance().subtract(amount));
        balance.setPendingBalance(balance.getPendingBalance().add(amount));
        balanceRepository.save(balance);

        log.info("✅ Withdrawal request created. Transaction ID: {}, Available: {}, Pending: {}",
                saved.getId(), balance.getAvailableBalance(), balance.getPendingBalance());

        return saved;
    }

    @Override
    @Transactional
    public Transaction approveWithdrawal(Long transactionId, Long adminId) {
        log.info("Approving withdrawal: transactionId={}, adminId={}", transactionId, adminId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new IllegalStateException("Transaction is not pending: " + transaction.getStatus());
        }

        if (transaction.getReason() != TransactionReason.WITHDRAW) {
            throw new IllegalStateException("Transaction is not a withdrawal: " + transaction.getReason());
        }

        // Update transaction status
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setProcessedAt(LocalDateTime.now());
        transaction.setProcessedBy(adminId);
        Transaction saved = transactionRepository.save(transaction);

        // Update balance: remove from pending (money already deducted from available when request was created)
        Balance balance = balanceRepository.findByEntityIdAndEntityType(
                        transaction.getEntityId(), transaction.getEntityType())
                .orElseThrow(() -> new ResourceNotFoundException("Balance not found"));

        balance.setPendingBalance(balance.getPendingBalance().subtract(transaction.getAmount()));
        balanceRepository.save(balance);

        log.info("✅ Withdrawal approved. Transaction ID: {}, Pending balance: {}",
                transactionId, balance.getPendingBalance());

        return saved;
    }

    @Override
    @Transactional
    public Transaction rejectWithdrawal(Long transactionId, Long adminId, RejectWithdrawalRequest request) {
        log.info("Rejecting withdrawal: transactionId={}, adminId={}", transactionId, adminId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new IllegalStateException("Transaction is not pending: " + transaction.getStatus());
        }

        if (transaction.getReason() != TransactionReason.WITHDRAW) {
            throw new IllegalStateException("Transaction is not a withdrawal: " + transaction.getReason());
        }

        // Update transaction status
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setProcessedAt(LocalDateTime.now());
        transaction.setProcessedBy(adminId);
        if (request != null && request.getReason() != null) {
            transaction.setDescription(transaction.getDescription() + " - Rejected: " + request.getReason());
        }
        Transaction saved = transactionRepository.save(transaction);

        // Restore balance: move from pending back to available
        Balance balance = balanceRepository.findByEntityIdAndEntityType(
                        transaction.getEntityId(), transaction.getEntityType())
                .orElseThrow(() -> new ResourceNotFoundException("Balance not found"));

        balance.setAvailableBalance(balance.getAvailableBalance().add(transaction.getAmount()));
        balance.setPendingBalance(balance.getPendingBalance().subtract(transaction.getAmount()));
        balanceRepository.save(balance);

        log.info("✅ Withdrawal rejected. Transaction ID: {}, Available: {}, Pending: {}",
                transactionId, balance.getAvailableBalance(), balance.getPendingBalance());

        return saved;
    }

    @Override
    @Transactional
    public Transaction reverseTransaction(Long transactionId, Long adminId, String reason) {
        log.info("Reversing transaction: transactionId={}, adminId={}", transactionId, adminId);

        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw new IllegalStateException("Can only reverse completed transactions");
        }

        // Create opposite transaction
        TransactionDirection oppositeDirection = original.getDirection() == TransactionDirection.CREDIT
                ? TransactionDirection.DEBIT
                : TransactionDirection.CREDIT;

        Transaction reversal = Transaction.builder()
                .entityId(original.getEntityId())
                .entityType(original.getEntityType())
                .orderId(original.getOrderId())
                .direction(oppositeDirection)
                .reason(original.getReason())
                .amount(original.getAmount())
                .description("Reversal of transaction #" + transactionId + 
                           (reason != null ? " - " + reason : ""))
                .status(TransactionStatus.COMPLETED)
                .processedBy(adminId)
                .build();

        Transaction saved = transactionRepository.save(reversal);

        // Update original transaction status
        original.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(original);

        // Update balance
        Balance balance = balanceRepository.findByEntityIdAndEntityType(
                        original.getEntityId(), original.getEntityType())
                .orElseThrow(() -> new ResourceNotFoundException("Balance not found"));

        updateBalanceFromTransaction(balance, saved);

        log.info("✅ Transaction reversed. Original ID: {}, Reversal ID: {}", transactionId, saved.getId());

        return saved;
    }

    @Override
    @Transactional
    public Transaction holdBalance(Long entityId, BigDecimal amount, String description) {
        return createTransaction(entityId, EntityType.SHIPPER, null,
                TransactionDirection.DEBIT, TransactionReason.HOLD, amount,
                description != null ? description : "Hold balance");
    }

    @Override
    @Transactional
    public Transaction releaseBalance(Long entityId, BigDecimal amount, String description) {
        // Get balance to check holding balance
        Balance balance = balanceRepository.findByEntityIdAndEntityType(entityId, EntityType.SHIPPER)
                .orElseThrow(() -> new ResourceNotFoundException("Balance not found for shipper: " + entityId));

        if (balance.getHoldingBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient holding balance. Holding: %s, Requested: %s",
                            balance.getHoldingBalance(), amount));
        }

        return createTransaction(entityId, EntityType.SHIPPER, null,
                TransactionDirection.CREDIT, TransactionReason.RELEASE, amount,
                description != null ? description : "Release balance");
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions(Long entityId, EntityType entityType) {
        log.info("Getting transactions for entity: {} ({})", entityId, entityType);
        return transactionRepository.findByEntityIdAndEntityTypeOrderByCreatedAtDesc(entityId, entityType)
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
        return transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PENDING)
                .stream()
                .filter(tx -> tx.getReason() == TransactionReason.WITHDRAW)
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions() {
        log.info("Getting all transactions");
        return transactionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update balance based on completed transaction
     */
    private void updateBalanceFromTransaction(Balance balance, Transaction transaction) {
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            return; // Only update balance for completed transactions
        }

        switch (transaction.getReason()) {
            case WITHDRAW:
                // Withdrawal already handled in requestWithdrawal and approveWithdrawal
                break;
            case HOLD:
                // Move from available to holding
                balance.setAvailableBalance(balance.getAvailableBalance().subtract(transaction.getAmount()));
                balance.setHoldingBalance(balance.getHoldingBalance().add(transaction.getAmount()));
                break;
            case RELEASE:
                // Move from holding to available
                balance.setHoldingBalance(balance.getHoldingBalance().subtract(transaction.getAmount()));
                balance.setAvailableBalance(balance.getAvailableBalance().add(transaction.getAmount()));
                break;
            default:
                // Normal CREDIT/DEBIT
                if (transaction.getDirection() == TransactionDirection.CREDIT) {
                    balance.setAvailableBalance(balance.getAvailableBalance().add(transaction.getAmount()));
                } else {
                    balance.setAvailableBalance(balance.getAvailableBalance().subtract(transaction.getAmount()));
                }
        }

        balanceRepository.save(balance);
    }
}
