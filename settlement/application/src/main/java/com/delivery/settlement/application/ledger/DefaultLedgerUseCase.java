package com.delivery.settlement.application.ledger;

import com.delivery.settlement.application.ledger.LedgerResourceMissing;
import com.delivery.settlement.application.api.ledger.LedgerStore;
import com.delivery.settlement.application.api.ledger.LedgerUseCase;
import com.delivery.settlement.domain.EntityType;
import com.delivery.settlement.domain.ledger.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Direction.*;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Reason.*;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Wallet.EARNINGS;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Wallet.DEPOSIT;

/** Financial orchestration; storage operations run in the adapter's existing transaction. */
public final class DefaultLedgerUseCase implements LedgerUseCase {
    private final LedgerStore store;
    private final Clock clock;
    public DefaultLedgerUseCase(LedgerStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public LedgerEntry create(LedgerPosting posting) {
        requirePositive(posting.amount(), "Amount must be greater than zero");
        var account = store.lockOrCreateAccount(new LedgerOwner(posting.entityId(), posting.entityType()));
        var saved = store.saveEntry(new LedgerEntry(null, posting, LedgerEntry.Status.COMPLETED, null, null, null));
        applyCompleted(account, saved);
        return saved;
    }

    @Override public LedgerEntry topUp(LedgerOwner owner, BigDecimal amount, String method) {
        requirePositive(amount, "Top-up amount must be greater than zero");
        var account = store.lockOrCreateAccount(owner);
        var posting = new LedgerPosting(owner.entityId(), owner.entityType(), null, CREDIT, DEPOSIT_TOPUP,
                amount, "Deposit top-up via " + (method != null ? method : "UNKNOWN"), DEPOSIT);
        var saved = store.saveEntry(new LedgerEntry(null, posting, LedgerEntry.Status.COMPLETED, null, null, null));
        store.saveAccount(account.withBalance(account.balance().topUp(amount)));
        return saved;
    }

    @Override public boolean checkCodEligibility(Long shipperId, BigDecimal amount) {
        if (shipperId == null || shipperId <= 0 || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Shipper ID and COD amount must be positive");
        }
        return store.readAccount(new LedgerOwner(shipperId, EntityType.SHIPPER))
                .map(account -> account.balance().canCoverCod(amount)).orElse(false);
    }

    @Override public LedgerEntry requestWithdrawal(LedgerOwner owner, BigDecimal amount) {
        requirePositive(amount, "Withdrawal amount must be greater than zero");
        var account = store.lockAccount(owner).orElseThrow(() -> new LedgerResourceMissing(
                "Balance not found for entity: " + owner.entityId() + " (" + owner.entityType() + ")"));
        var reserved = account.balance().reserveWithdrawal(amount);
        var posting = new LedgerPosting(owner.entityId(), owner.entityType(), null, DEBIT, WITHDRAW,
                amount, "Withdrawal request", EARNINGS);
        var saved = store.saveEntry(new LedgerEntry(null, posting, LedgerEntry.Status.PENDING, null, null, null));
        store.saveAccount(account.withBalance(reserved));
        return saved;
    }

    @Override public LedgerEntry approveWithdrawal(Long entryId, Long adminId) {
        var entry = requireEntry(entryId);
        entry.requirePendingWithdrawal();
        var saved = store.saveEntry(entry.processed(LedgerEntry.Status.COMPLETED, LocalDateTime.now(clock),
                adminId, entry.posting().description()));
        var account = requireAccount(entry.owner());
        store.saveAccount(account.withBalance(account.balance().approveWithdrawal(entry.posting().amount())));
        return saved;
    }

    @Override public LedgerEntry rejectWithdrawal(Long entryId, Long adminId, String reason) {
        var entry = requireEntry(entryId);
        entry.requirePendingWithdrawal();
        String description = entry.posting().description();
        if (reason != null) description += " - Rejected: " + reason;
        var saved = store.saveEntry(entry.processed(LedgerEntry.Status.FAILED, LocalDateTime.now(clock), adminId, description));
        var account = requireAccount(entry.owner());
        store.saveAccount(account.withBalance(account.balance().rejectWithdrawal(entry.posting().amount())));
        return saved;
    }

    @Override public LedgerEntry reverse(Long entryId, Long adminId, String reason) {
        var original = requireEntry(entryId);
        original.requireReversible();
        var posting = original.posting();
        var reversal = new LedgerPosting(posting.entityId(), posting.entityType(), posting.orderId(),
                posting.direction() == CREDIT ? DEBIT : CREDIT, posting.reason(), posting.amount(),
                "Reversal of transaction #" + entryId + (reason != null ? " - " + reason : ""), posting.wallet());
        var saved = store.saveEntry(new LedgerEntry(null, reversal, LedgerEntry.Status.COMPLETED, null, adminId, null));
        store.saveEntry(original.reversed());
        applyCompleted(requireAccount(original.owner()), saved);
        return saved;
    }

    @Override public LedgerEntry hold(Long shipperId, BigDecimal amount, String description) {
        return create(new LedgerPosting(shipperId, EntityType.SHIPPER, null, DEBIT, HOLD, amount,
                description != null ? description : "Hold balance", EARNINGS));
    }

    @Override public LedgerEntry release(Long shipperId, BigDecimal amount, String description) {
        var owner = new LedgerOwner(shipperId, EntityType.SHIPPER);
        var account = store.lockAccount(owner).orElseThrow(() -> new LedgerResourceMissing(
                "Balance not found for shipper: " + shipperId));
        account.balance().requireReleaseCapacity(amount);
        return create(new LedgerPosting(shipperId, EntityType.SHIPPER, null, CREDIT, RELEASE, amount,
                description != null ? description : "Release balance", EARNINGS));
    }

    private void applyCompleted(LedgerAccount account, LedgerEntry entry) {
        if (entry.status() == LedgerEntry.Status.COMPLETED) {
            store.saveAccount(account.withBalance(account.balance().apply(entry.posting())));
        }
    }
    private LedgerEntry requireEntry(Long id) {
        return store.findEntry(id).orElseThrow(() -> new LedgerResourceMissing("Transaction not found with id: " + id));
    }
    private LedgerAccount requireAccount(LedgerOwner owner) {
        return store.lockAccount(owner).orElseThrow(() -> new LedgerResourceMissing("Balance not found"));
    }
    private void requirePositive(BigDecimal amount, String message) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException(message);
    }
}
