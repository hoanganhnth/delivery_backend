package com.delivery.settlement.application.ledger;

import com.delivery.settlement.application.api.ledger.BalanceAccountStore;
import com.delivery.settlement.domain.EntityType;
import com.delivery.settlement.domain.ledger.*;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultBalanceAccountUseCaseTest {
    private final LedgerOwner owner = new LedgerOwner(22L, EntityType.SHIPPER);
    @Test void createsZeroWalletsOnceAndRetainsExistingMoneyOnRepeatedRead() {
        var store = new Store();
        var core = new DefaultBalanceAccountUseCase(store);
        assertEquals(WalletBalance.zero(), core.ensureAccount(owner).balance());
        store.account = store.account.withBalance(store.account.balance().topUp(BigDecimal.TEN));
        assertEquals(BigDecimal.TEN, core.ensureAccount(owner).balance().deposit());
        assertEquals(1, store.inserts);
    }
    @Test void creationConflictReturnsWinnerButMissingWinnerFailsClosed() {
        var store = new Store();
        store.conflict = true;
        var core = new DefaultBalanceAccountUseCase(store);
        assertEquals(owner, core.ensureAccount(owner).owner());
        store.account = null;
        store.missingWinner = true;
        assertThrows(LedgerResourceMissing.class, () -> core.ensureAccount(owner));
    }
    private static class Store implements BalanceAccountStore {
        LedgerAccount account;
        boolean conflict, missingWinner;
        int inserts;
        public Optional<LedgerAccount> findAccount(LedgerOwner owner) { return Optional.ofNullable(account); }
        public LedgerAccount insertAccount(LedgerOwner owner, WalletBalance initial) {
            inserts++;
            if (!missingWinner) account = new LedgerAccount(1L, owner, initial);
            if (conflict) throw new BalanceInsertConflict(new IllegalStateException("unique account"));
            return account;
        }
    }
}
