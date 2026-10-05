package com.delivery.promotion.application;

import com.delivery.promotion.application.api.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReservationUseCasesTest {
    private static final UUID ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);

    @Test void reserveChecksBothReplaysBeforeSortedWalletVoucherPairsThenQuoteAndPersistence() {
        var port = new ReserveFake();
        assertThat(new ReserveVouchersUseCase().reserve(port)).isEqualTo("new");
        assertThat(port.calls).containsExactly("prepare", "id", "order", "wallet1", "voucher1", "capacity",
                "wallet3", "voucher3", "capacity", "quote", "persist");
    }
    @Test void replayByIdOrOrderNeverLocksQuotesOrSaves() {
        for (boolean byId : List.of(true, false)) {
            var port = new ReserveFake(); port.idReplay = byId; port.orderReplay = !byId;
            assertThat(new ReserveVouchersUseCase().reserve(port)).isEqualTo("stored");
            assertThat(port.calls).containsExactlyElementsOf(byId ? List.of("prepare", "id", "replay", "result")
                    : List.of("prepare", "id", "order", "replay", "result"));
        }
    }
    @Test void conflictingReplayAndClaimFenceFailBeforeLaterEffects() {
        var port = new ReserveFake(); port.idReplay = true; port.badReplay = true;
        assertThatThrownBy(() -> new ReserveVouchersUseCase().reserve(port)).hasMessage("contradictory");
        assertThat(port.calls).containsExactly("prepare", "id", "replay");
        for (String state : Arrays.asList("RESERVED", "USED", null)) {
            var claim = new ReserveFake(); claim.walletState = state;
            assertThatThrownBy(() -> new ReserveVouchersUseCase().reserve(claim)).hasMessage("Voucher is already reserved or used");
            assertThat(claim.calls).containsExactly("prepare", "id", "order", "wallet1");
        }
        var capacity = new ReserveFake(); capacity.capacityFailure = true;
        assertThatThrownBy(() -> new ReserveVouchersUseCase().reserve(capacity)).hasMessage("capacity");
        assertThat(capacity.calls).containsExactly("prepare", "id", "order", "wallet1", "voucher1", "capacity");
    }
    @Test void quoteAndPersistenceFailuresPropagateWithoutRetryingEffects() {
        var quote = new ReserveFake(); quote.quoteFailure = true;
        assertThatThrownBy(() -> new ReserveVouchersUseCase().reserve(quote)).hasMessage("quote failed");
        assertThat(quote.calls).doesNotContain("persist");
        var save = new ReserveFake(); save.saveFailure = true;
        assertThatThrownBy(() -> new ReserveVouchersUseCase().reserve(save)).hasMessage("save failed");
        assertThat(Collections.frequency(save.calls, "persist")).isEqualTo(1);
    }
    @Test void commitUsesLockedStateAndFreshClockAndNoopsOnCommitted() {
        for (boolean bulk : List.of(false, true)) {
            var port = new TransitionFake();
            assertThat(new ReservationTransitionUseCase().transition(new PromotionCommands.Transition(bulk, true), port)).isEqualTo("result");
            assertThat(port.calls).containsExactly("lock", "state", "now", "COMMITTED", "result");
            port = new TransitionFake(); port.state = "COMMITTED";
            new ReservationTransitionUseCase().transition(new PromotionCommands.Transition(bulk, true), port);
            assertThat(port.calls).containsExactly("lock", "state", "now", "result");
            var expired = new TransitionFake(); expired.expires = NOW;
            assertThatThrownBy(() -> new ReservationTransitionUseCase().transition(new PromotionCommands.Transition(bulk, true), expired))
                    .hasMessage((bulk ? "Promotion" : "Voucher") + " reservation expired before commit");
            assertThat(expired.calls).containsExactly("lock", "state", "now");
            var released = new TransitionFake(); released.state = "RELEASED";
            assertThatThrownBy(() -> new ReservationTransitionUseCase().transition(new PromotionCommands.Transition(bulk, true), released))
                    .hasMessage((bulk ? "Promotion" : "Voucher") + " reservation cannot be committed from state RELEASED");
        }
    }
    @Test void releaseOnlyMutatesReservedOrCommittedWithoutReadingClock() {
        for (String state : List.of("RESERVED", "COMMITTED", "RELEASED", "EXPIRED")) {
            var port = new TransitionFake(); port.state = state;
            new ReservationTransitionUseCase().transition(new PromotionCommands.Transition(false, false), port);
            assertThat(port.calls).containsExactlyElementsOf(state.equals("RESERVED") || state.equals("COMMITTED")
                    ? List.of("lock", "state", "RELEASED", "result") : List.of("lock", "state", "result"));
        }
    }
    @Test void expiryRechecksMissingTerminalAndExtendedHoldsUnderLock() {
        var port = new TransitionFake();
        assertThat(new ReservationTransitionUseCase().expire(port)).isEqualTo(1);
        assertThat(port.calls).containsExactly("candidates", "lockexpired", "state", "now", "EXPIRED",
                "lockmissing", "lockterminal", "state", "now", "lockextended", "state", "now");
    }

    static class ReserveFake implements ReservationPort<String, String, Long, Long, String> {
        final List<String> calls = new ArrayList<>();
        boolean idReplay, orderReplay, badReplay, capacityFailure, quoteFailure, saveFailure;
        String walletState = "SAVED";
        public PromotionCommands.Reserve prepare() { calls.add("prepare"); return new PromotionCommands.Reserve(ID, 5L, List.of(1L, 3L)); }
        public Optional<String> findById() { calls.add("id"); return idReplay ? Optional.of("stored") : Optional.empty(); }
        public Optional<String> findByOrder() { calls.add("order"); return orderReplay ? Optional.of("stored") : Optional.empty(); }
        public void requireExactReplay(String stored, PromotionCommands.Reserve command) { calls.add("replay"); if (badReplay) throw new IllegalArgumentException("contradictory"); }
        public String replayResult(String stored) { calls.add("result"); return stored; }
        public Long lockWallet(Long id) { calls.add("wallet" + id); return id; }
        public String walletStatus(Long wallet) { return walletState; }
        public Long lockVoucher(Long id) { calls.add("voucher" + id); return id; }
        public void requireCapacity(Long wallet, Long voucher) { calls.add("capacity"); if (capacityFailure) throw new IllegalStateException("capacity"); }
        public String quote(Map<Long, Long> vouchers, PromotionCommands.Reserve command) {
            calls.add("quote"); assertThat(vouchers.keySet()).containsExactly(1L, 3L);
            if (quoteFailure) throw new IllegalArgumentException("quote failed"); return "quote";
        }
        public String persist(Map<Long, Long> wallets, Map<Long, Long> vouchers, String quote, PromotionCommands.Reserve command) {
            calls.add("persist"); assertThat(wallets.keySet()).containsExactly(1L, 3L); assertThat(quote).isEqualTo("quote");
            if (saveFailure) throw new IllegalStateException("save failed"); return "new";
        }
        public RuntimeException conflict(String message) { return new IllegalStateException(message); }
    }
    static class TransitionFake implements ReservationTransitionPort<String, String> {
        final List<String> calls = new ArrayList<>();
        String state = "RESERVED"; LocalDateTime expires = NOW.plusMinutes(15);
        public String lock() { calls.add("lock"); return "reservation"; }
        public PromotionCommands.ReservationState state(String reservation) {
            calls.add("state");
            return new PromotionCommands.ReservationState(reservation.equals("terminal") ? "COMMITTED" : state,
                    reservation.equals("expired") ? NOW : expires);
        }
        public LocalDateTime now() { calls.add("now"); return NOW; }
        public void transition(String reservation, String target) { calls.add(target); }
        public String result(String reservation) { calls.add("result"); return "result"; }
        public RuntimeException conflict(String message) { return new IllegalStateException(message); }
        public List<String> expiryCandidates() { calls.add("candidates"); return List.of("expired", "missing", "terminal", "extended"); }
        public String lockCandidate(String candidate) { calls.add("lock" + candidate); return candidate.equals("missing") ? null : candidate; }
    }
}
