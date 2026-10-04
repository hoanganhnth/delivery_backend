package com.delivery.settlement.application.api.ledger;

import com.delivery.settlement.domain.ledger.*;
import java.math.BigDecimal;

public interface LedgerUseCase {
    int COMPATIBILITY_LIST_LIMIT = 100;
    LedgerEntry create(LedgerPosting posting);
    LedgerEntry topUp(LedgerOwner owner, BigDecimal amount, String paymentMethod);
    boolean checkCodEligibility(Long shipperId, BigDecimal amount);
    LedgerEntry requestWithdrawal(LedgerOwner owner, BigDecimal amount);
    LedgerEntry approveWithdrawal(Long entryId, Long adminId);
    LedgerEntry rejectWithdrawal(Long entryId, Long adminId, String reason);
    LedgerEntry reverse(Long entryId, Long adminId, String reason);
    LedgerEntry hold(Long shipperId, BigDecimal amount, String description);
    LedgerEntry release(Long shipperId, BigDecimal amount, String description);
}
