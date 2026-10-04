package com.delivery.settlement_service.adapter;

import com.delivery.settlement.application.api.ledger.LedgerStore;
import com.delivery.settlement.domain.ledger.*;
import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.TransactionStatus;
import com.delivery.settlement_service.entity.Transaction.WalletType;
import com.delivery.settlement_service.exception.ResourceNotFoundException;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.BalanceService;
import java.util.Optional;

/** Persistence mapping and row locks; account/entry mutation decisions live in the core. */
public final class JpaLedgerAdapter implements LedgerStore {
    private final TransactionRepository transactions;
    private final BalanceRepository balances;
    private final BalanceService balanceCreation;
    public JpaLedgerAdapter(TransactionRepository transactions, BalanceRepository balances, BalanceService balanceCreation) {
        this.transactions = transactions;
        this.balances = balances;
        this.balanceCreation = balanceCreation;
    }
    @Override public LedgerAccount lockOrCreateAccount(LedgerOwner owner) {
        return lockAccount(owner).orElseGet(() -> {
            // Preserve the existing REQUIRES_NEW creation and subsequent pessimistic re-read.
            balanceCreation.createBalance(owner.entityId(), enumValue(owner.entityType(), EntityType.class));
            return lockAccount(owner).orElseThrow(() -> new ResourceNotFoundException(
                    "Balance not found after creation for entity: " + owner.entityId() + " (" + owner.entityType() + ")"));
        });
    }
    @Override public Optional<LedgerAccount> lockAccount(LedgerOwner owner) {
        return balances.findByEntityIdAndEntityTypeForUpdate(owner.entityId(), enumValue(owner.entityType(), EntityType.class)).map(this::account);
    }
    @Override public Optional<LedgerAccount> readAccount(LedgerOwner owner) {
        return balances.findByEntityIdAndEntityType(owner.entityId(), enumValue(owner.entityType(), EntityType.class)).map(this::account);
    }
    @Override public void saveAccount(LedgerAccount account) {
        // The row is already managed and locked in this transaction; findById resolves its identity.
        Balance row = balances.findById(account.id()).orElseThrow(() -> new ResourceNotFoundException("Balance not found"));
        var value = account.balance();
        row.setAvailableBalance(value.available());
        row.setPendingBalance(value.pending());
        row.setHoldingBalance(value.holding());
        row.setDepositBalance(value.deposit());
        row.setReservedDepositBalance(value.reservedDeposit());
        row.setTotalDeposited(value.totalDeposited());
        row.setTotalCodCollected(value.totalCodCollected());
        balances.save(row);
    }
    @Override public Optional<LedgerEntry> findEntry(Long id) { return transactions.findById(id).map(this::entry); }
    @Override public LedgerEntry saveEntry(LedgerEntry entry) {
        Transaction row = entry.id() == null ? new Transaction() : requireEntity(entry.id());
        var posting = entry.posting();
        row.setEntityId(posting.entityId());
        row.setEntityType(enumValue(posting.entityType(), EntityType.class));
        row.setOrderId(posting.orderId());
        row.setDirection(enumValue(posting.direction(), TransactionDirection.class));
        row.setReason(enumValue(posting.reason(), TransactionReason.class));
        row.setAmount(posting.amount());
        row.setDescription(posting.description());
        row.setWalletType(enumValue(posting.wallet(), WalletType.class));
        row.setStatus(enumValue(entry.status(), TransactionStatus.class));
        row.setProcessedAt(entry.processedAt());
        row.setProcessedBy(entry.processedBy());
        row.setCreatedAt(entry.createdAt());
        return entry(transactions.save(row));
    }
    public Transaction requireEntity(Long id) {
        return transactions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));
    }
    private LedgerAccount account(Balance row) {
        return new LedgerAccount(row.getId(), new LedgerOwner(row.getEntityId(),
                enumValue(row.getEntityType(), com.delivery.settlement.domain.EntityType.class)),
                new WalletBalance(row.getAvailableBalance(), row.getPendingBalance(), row.getHoldingBalance(),
                        row.getDepositBalance(), row.getReservedDepositBalance(), row.getTotalDeposited(), row.getTotalCodCollected()));
    }
    private LedgerEntry entry(Transaction row) {
        var posting = new LedgerPosting(row.getEntityId(), enumValue(row.getEntityType(), com.delivery.settlement.domain.EntityType.class),
                row.getOrderId(), enumValue(row.getDirection(), LedgerPosting.Direction.class), enumValue(row.getReason(), LedgerPosting.Reason.class),
                row.getAmount(), row.getDescription(), enumValue(row.getWalletType(), LedgerPosting.Wallet.class));
        return new LedgerEntry(row.getId(), posting, enumValue(row.getStatus(), LedgerEntry.Status.class),
                row.getProcessedAt(), row.getProcessedBy(), row.getCreatedAt());
    }
    public static <T extends Enum<T>> T enumValue(Enum<?> value, Class<T> type) {
        return value == null ? null : Enum.valueOf(type, value.name());
    }
}
