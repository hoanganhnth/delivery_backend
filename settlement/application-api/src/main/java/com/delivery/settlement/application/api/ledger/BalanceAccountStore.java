package com.delivery.settlement.application.api.ledger;

import com.delivery.settlement.domain.ledger.*;
import java.util.Optional;

public interface BalanceAccountStore {
    Optional<LedgerAccount> findAccount(LedgerOwner owner);
    LedgerAccount insertAccount(LedgerOwner owner, WalletBalance initialBalance);
}
