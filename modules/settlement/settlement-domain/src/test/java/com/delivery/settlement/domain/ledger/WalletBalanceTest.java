package com.delivery.settlement.domain.ledger;

import com.delivery.settlement.domain.EntityType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Direction.*;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Reason.*;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Wallet.EARNINGS;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Wallet.DEPOSIT;

class WalletBalanceTest {
    private WalletBalance funded() {
        return new WalletBalance(new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("10"),
                new BigDecimal("150"), new BigDecimal("30"), new BigDecimal("200"), new BigDecimal("50"));
    }
    private LedgerPosting posting(LedgerPosting.Reason reason, LedgerPosting.Direction direction, LedgerPosting.Wallet wallet) {
        return new LedgerPosting(1L, EntityType.SHIPPER, null, direction, reason, new BigDecimal("10"), "test", wallet);
    }
    @Test void codDebitOnlyConsumesDepositAndTracksCashWithoutCreatingNegativeDeposit() {
        var after = funded().apply(posting(COD_SETTLEMENT, DEBIT, DEPOSIT));
        assertEquals(new BigDecimal("140"), after.deposit());
        assertEquals(new BigDecimal("60"), after.totalCodCollected());
        assertEquals(new BigDecimal("100"), after.available());
        var over = new LedgerPosting(1L, EntityType.SHIPPER, null, DEBIT, COD_SETTLEMENT, new BigDecimal("151"), "test", DEPOSIT);
        assertThrows(InsufficientWalletFunds.class, () -> funded().apply(over));
        assertEquals(funded(), funded().apply(posting(COD_SETTLEMENT, CREDIT, DEPOSIT)));
    }
    @Test void withdrawalReserveApproveAndRejectKeepWalletAccounting() {
        var reserved = funded().reserveWithdrawal(new BigDecimal("10"));
        assertEquals(new BigDecimal("90"), reserved.available());
        assertEquals(new BigDecimal("30"), reserved.pending());
        assertEquals(new BigDecimal("20"), reserved.approveWithdrawal(new BigDecimal("10")).pending());
        assertEquals(funded(), reserved.rejectWithdrawal(new BigDecimal("10")));
        assertThrows(InsufficientWalletFunds.class, () -> funded().reserveWithdrawal(new BigDecimal("101")));
    }
    @Test void holdReleaseAndTopupPreserveDistinctCounters() {
        var held = funded().apply(posting(HOLD, DEBIT, EARNINGS));
        assertEquals(new BigDecimal("90"), held.available());
        assertEquals(new BigDecimal("20"), held.holding());
        assertEquals(funded(), held.apply(posting(RELEASE, CREDIT, EARNINGS)));
        var topup = funded().topUp(new BigDecimal("10"));
        assertEquals(new BigDecimal("160"), topup.deposit());
        assertEquals(new BigDecimal("210"), topup.totalDeposited());
        assertEquals(funded(), funded().apply(posting(DEPOSIT_TOPUP, CREDIT, DEPOSIT)));
        assertEquals(funded(), funded().apply(posting(WITHDRAW, DEBIT, EARNINGS)));
    }
    @Test void ordinaryPostingsChooseDirectionAndWalletWithoutChangingOtherCounters() {
        assertEquals(new BigDecimal("110"), funded().apply(posting(ORDER_EARNING, CREDIT, EARNINGS)).available());
        assertEquals(new BigDecimal("90"), funded().apply(posting(PENALTY, DEBIT, EARNINGS)).available());
        assertEquals(new BigDecimal("160"), funded().apply(posting(ADJUSTMENT_CREDIT, CREDIT, DEPOSIT)).deposit());
        assertEquals(new BigDecimal("140"), funded().apply(posting(ADJUSTMENT_DEBIT, DEBIT, DEPOSIT)).deposit());
    }
    @Test void eligibilityUsesUnreservedDepositAndNullReservedRetainsExistingZeroDefault() {
        assertTrue(funded().canCoverCod(new BigDecimal("120")));
        assertFalse(funded().canCoverCod(new BigDecimal("121")));
        var legacy = new WalletBalance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("150"), null, BigDecimal.ZERO, BigDecimal.ZERO);
        assertTrue(legacy.canCoverCod(new BigDecimal("150")));
    }
}
