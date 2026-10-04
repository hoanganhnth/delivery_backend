package com.delivery.settlement.domain.ledger;

import com.delivery.settlement.domain.EntityType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LedgerEntryTest {
    private LedgerEntry entry(LedgerEntry.Status status, LedgerPosting.Reason reason) {
        return new LedgerEntry(1L, new LedgerPosting(22L, EntityType.SHIPPER, null, LedgerPosting.Direction.DEBIT,
                reason, BigDecimal.TEN, "original", LedgerPosting.Wallet.EARNINGS), status, null, null, null);
    }
    @Test void withdrawalAdmissionRequiresPendingStatusAndWithdrawalReason() {
        assertDoesNotThrow(() -> entry(LedgerEntry.Status.PENDING, LedgerPosting.Reason.WITHDRAW).requirePendingWithdrawal());
        assertThrows(IllegalStateException.class, () -> entry(LedgerEntry.Status.COMPLETED, LedgerPosting.Reason.WITHDRAW).requirePendingWithdrawal());
        assertThrows(IllegalStateException.class, () -> entry(LedgerEntry.Status.PENDING, LedgerPosting.Reason.HOLD).requirePendingWithdrawal());
    }
    @Test void processingAndReversalRetainImmutablePostingIdentityAndOwner() {
        var initial = entry(LedgerEntry.Status.COMPLETED, LedgerPosting.Reason.WITHDRAW);
        assertDoesNotThrow(initial::requireReversible);
        assertThrows(IllegalStateException.class, () -> initial.reversed().requireReversible());
        var processed = initial.processed(LedgerEntry.Status.FAILED, LocalDateTime.of(2026, 1, 1, 0, 0), 99L, "rejected");
        assertEquals("rejected", processed.posting().description());
        assertEquals(99L, processed.processedBy());
        assertEquals(LedgerEntry.Status.REVERSED, processed.reversed().status());
        assertEquals(new LedgerOwner(22L, EntityType.SHIPPER), processed.owner());
        var account = new LedgerAccount(1L, processed.owner(), WalletBalance.zero());
        assertEquals(account, account.withBalance(WalletBalance.zero()));
        assertDoesNotThrow(() -> account.balance().requireReleaseCapacity(BigDecimal.ZERO));
        assertThrows(InsufficientWalletFunds.class, () -> account.balance().requireReleaseCapacity(BigDecimal.ONE));
    }
}
