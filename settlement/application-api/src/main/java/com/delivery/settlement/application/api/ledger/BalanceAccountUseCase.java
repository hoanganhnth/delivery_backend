package com.delivery.settlement.application.api.ledger;

import com.delivery.settlement.domain.ledger.*;

public interface BalanceAccountUseCase {
    int COMPATIBILITY_LIST_LIMIT = 100;
    LedgerAccount ensureAccount(LedgerOwner owner);
}
