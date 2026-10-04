package com.delivery.settlement.application.ledger;

import com.delivery.settlement.application.api.ledger.*;
import com.delivery.settlement.domain.EntityType;
import com.delivery.settlement.domain.ledger.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Direction.*;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Reason.*;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Wallet.EARNINGS;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Wallet.DEPOSIT;

class DefaultLedgerUseCaseTest {
    private final LedgerOwner owner = new LedgerOwner(22L, EntityType.SHIPPER);
    private final Store store = new Store();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final LedgerUseCase core = new DefaultLedgerUseCase(store, clock);
    private BigDecimal money(String value) { return new BigDecimal(value); }
    private LedgerPosting posting(BigDecimal amount) {
        return new LedgerPosting(22L, EntityType.SHIPPER, null, CREDIT, DELIVERY_FEE, amount, "fee", EARNINGS);
    }
    @Test void postingAndTopupCreateAccountsAndKeepWalletsDistinct() {
        core.create(posting(money("10")));
        core.topUp(owner, money("150"), null);
        assertEquals(money("10"), store.account.balance().available());
        assertEquals(money("150"), store.account.balance().deposit());
        assertEquals(money("150"), store.account.balance().totalDeposited());
        assertEquals("Deposit top-up via UNKNOWN", store.entries.get(2L).posting().description());
        core.topUp(owner, money("1"), "VNPay");
        assertEquals("Deposit top-up via VNPay", store.entries.get(3L).posting().description());
    }
    @Test void withdrawApproveAndRejectUseExistingPendingStateMachine() {
        core.create(posting(money("100")));
        var requested = core.requestWithdrawal(owner, money("30"));
        assertEquals(LedgerEntry.Status.PENDING, requested.status());
        assertEquals(money("70"), store.account.balance().available());
        assertEquals(money("30"), store.account.balance().pending());
        var rejected = core.rejectWithdrawal(requested.id(), 99L, "bank unavailable");
        assertEquals("Withdrawal request - Rejected: bank unavailable", rejected.posting().description());
        assertEquals(LedgerEntry.Status.FAILED, rejected.status());
        assertEquals(money("100"), store.account.balance().available());
        var next = core.requestWithdrawal(owner, money("25"));
        var approved = core.approveWithdrawal(next.id(), 99L);
        assertEquals(LedgerEntry.Status.COMPLETED, approved.status());
        assertEquals(LocalDateTime.ofInstant(clock.instant(), clock.getZone()), approved.processedAt());
        assertEquals(99L, approved.processedBy());
        assertEquals(money("75"), store.account.balance().available());
        assertEquals(BigDecimal.ZERO, store.account.balance().pending());
        assertThrows(IllegalStateException.class, () -> core.approveWithdrawal(next.id(), 99L));
    }
    @Test void holdReleaseAndReversalPreserveExistingDescriptionsAndOriginalStatus() {
        var original = core.create(posting(money("100")));
        core.hold(22L, money("20"), null);
        assertEquals(money("20"), store.account.balance().holding());
        core.release(22L, money("20"), null);
        assertEquals(BigDecimal.ZERO, store.account.balance().holding());
        var reversal = core.reverse(original.id(), 99L, "correction");
        assertEquals(DEBIT, reversal.posting().direction());
        assertEquals("Reversal of transaction #1 - correction", reversal.posting().description());
        assertEquals(LedgerEntry.Status.REVERSED, store.entries.get(original.id()).status());
        assertEquals(BigDecimal.ZERO, store.account.balance().available());
        assertThrows(IllegalStateException.class, () -> core.reverse(original.id(), 99L, null));
        assertThrows(InsufficientWalletFunds.class, () -> core.release(22L, money("1"), null));
    }
    @Test void insufficientOrInvalidAmountsFailAndEligibilityRequiresExistingCapacity() {
        assertFalse(core.checkCodEligibility(22L, money("1")));
        assertThrows(LedgerResourceMissing.class, () -> core.requestWithdrawal(owner, money("1")));
        assertThrows(LedgerResourceMissing.class, () -> core.release(22L, money("1"), "custom"));
        core.topUp(owner, money("100"), null);
        assertTrue(core.checkCodEligibility(22L, money("100")));
        assertFalse(core.checkCodEligibility(22L, money("101")));
        assertThrows(InsufficientWalletFunds.class, () -> core.requestWithdrawal(owner, money("1")));
        for (BigDecimal invalid : Arrays.asList(null, BigDecimal.ZERO, money("-1"))) {
            assertThrows(IllegalArgumentException.class, () -> core.create(posting(invalid)));
            assertThrows(IllegalArgumentException.class, () -> core.topUp(owner, invalid, null));
            assertThrows(IllegalArgumentException.class, () -> core.requestWithdrawal(owner, invalid));
            assertThrows(IllegalArgumentException.class, () -> core.checkCodEligibility(22L, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> core.checkCodEligibility(null, money("1")));
        assertThrows(IllegalArgumentException.class, () -> core.checkCodEligibility(0L, money("1")));
        assertThrows(LedgerResourceMissing.class, () -> core.approveWithdrawal(999L, 1L));
        assertThrows(LedgerResourceMissing.class, () -> core.reverse(999L, 1L, null));
    }
    @Test void pendingNonWithdrawalCannotBeApprovedAndNullRejectReasonIsNotAppended() {
        var entry = core.create(posting(money("100")));
        store.entries.put(entry.id(), entry.processed(LedgerEntry.Status.PENDING, null, null, "fee"));
        assertThrows(IllegalStateException.class, () -> core.approveWithdrawal(entry.id(), 1L));
        var requested = core.requestWithdrawal(owner, money("1"));
        assertEquals("Withdrawal request", core.rejectWithdrawal(requested.id(), 1L, null).posting().description());
        var debit = core.hold(22L, money("2"), "custom hold");
        assertEquals(CREDIT, core.reverse(debit.id(), 1L, null).posting().direction());
        core.release(22L, money("2"), "custom release");
    }
    private final class Store implements LedgerStore {
        LedgerAccount account;
        Map<Long, LedgerEntry> entries = new LinkedHashMap<>();
        public LedgerAccount lockOrCreateAccount(LedgerOwner owner) {
            if (account == null) account = new LedgerAccount(1L, owner, WalletBalance.zero());
            return account;
        }
        public Optional<LedgerAccount> lockAccount(LedgerOwner owner) { return Optional.ofNullable(account); }
        public Optional<LedgerAccount> readAccount(LedgerOwner owner) { return Optional.ofNullable(account); }
        public void saveAccount(LedgerAccount next) { account = next; }
        public Optional<LedgerEntry> findEntry(Long id) { return Optional.ofNullable(entries.get(id)); }
        public LedgerEntry saveEntry(LedgerEntry entry) {
            var id = entry.id() == null ? (long) entries.size() + 1 : entry.id();
            var saved = new LedgerEntry(id, entry.posting(), entry.status(), entry.processedAt(), entry.processedBy(), entry.createdAt());
            entries.put(id, saved);
            return saved;
        }
    }
}
