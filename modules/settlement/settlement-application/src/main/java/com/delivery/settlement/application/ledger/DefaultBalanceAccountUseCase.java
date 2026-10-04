package com.delivery.settlement.application.ledger;

import com.delivery.settlement.application.api.ledger.BalanceAccountStore;
import com.delivery.settlement.application.api.ledger.BalanceAccountUseCase;
import com.delivery.settlement.domain.ledger.*;
import java.util.Objects;

public final class DefaultBalanceAccountUseCase implements BalanceAccountUseCase {
    private final BalanceAccountStore store;
    public DefaultBalanceAccountUseCase(BalanceAccountStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }
    @Override public LedgerAccount ensureAccount(LedgerOwner owner) {
        return store.findAccount(owner).orElseGet(() -> {
            try { return store.insertAccount(owner, WalletBalance.zero()); }
            catch (BalanceInsertConflict conflict) {
                return store.findAccount(owner).orElseThrow(() -> new LedgerResourceMissing(
                        "Balance not found with entityId: " + owner.entityId()));
            }
        });
    }
}
