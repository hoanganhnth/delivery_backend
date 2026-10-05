package com.delivery.promotion.application;

import com.delivery.promotion.application.api.*;
import com.delivery.promotion.domain.OrderReservationEventPolicy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OrderReservationEventUseCaseTest {
    private static final UUID EVENT = UUID.randomUUID(), LEGACY = UUID.randomUUID(), BULK = UUID.randomUUID();
    private PromotionCommands.OrderEvent event(String action, UUID legacy, UUID bulk, String previous) {
        return new PromotionCommands.OrderEvent(EVENT, "order.created", action, 5L, legacy, bulk, previous, "hash");
    }
    @Test void receiptClaimPrecedesEachOperationAndBulkWinsWhenBothIdsPresent() {
        for (boolean bulk : List.of(false, true)) {
            for (String action : List.of("COMMIT", "RELEASE")) {
                var port = new EventFake(); var event = event(action, LEGACY, bulk ? BULK : null, "CREATED");
                new ProcessOrderReservationEventUseCase().process(event, port);
                assertThat(port.calls).containsExactly("claim", action + (bulk ? "bulk" : "legacy"));
                assertThat(port.operatedId).isEqualTo(bulk ? BULK : LEGACY);
            }
        }
    }
    @Test void receiptOnlyEventsAndPostFulfilmentCompensationNeverTouchReservation() {
        for (var event : List.of(event("COMMIT", null, null, ""), event("RELEASE", LEGACY, BULK, "delivered"))) {
            var port = new EventFake(); new ProcessOrderReservationEventUseCase().process(event, port);
            assertThat(port.calls).containsExactly("claim");
        }
    }
    @Test void exactReceiptReplayReturnsBeforeDomainOperationAndContradictionFailsClosed() {
        var command = event("COMMIT", LEGACY, BULK, "");
        var port = new EventFake(); port.claim = false;
        port.receipt = new OrderReservationEventPolicy.Receipt(command.source(), command.action(), command.orderId(), LEGACY, "hash");
        new ProcessOrderReservationEventUseCase().process(command, port);
        assertThat(port.calls).containsExactly("claim", "existing");
        port.calls.clear(); port.receipt = new OrderReservationEventPolicy.Receipt(command.source(), command.action(), command.orderId(), LEGACY, "different");
        assertThatThrownBy(() -> new ProcessOrderReservationEventUseCase().process(command, port))
                .hasMessage("eventId replay has a contradictory voucher reservation payload");
        assertThat(port.calls).containsExactly("claim", "existing");
    }
    @Test void uncommittedOrNullCommitResponseFailsWithExistingRailMessage() {
        for (boolean bulk : List.of(false, true)) {
            for (String state : Arrays.asList(null, "RESERVED")) {
                var port = new EventFake(); port.state = state;
                assertThatThrownBy(() -> new ProcessOrderReservationEventUseCase().process(event("COMMIT", LEGACY, bulk ? BULK : null, ""), port))
                        .hasMessage((bulk ? "Promotion" : "Voucher") + " reservation did not reach COMMITTED state");
            }
        }
    }
    @Test void missingWinnerOrTransitionFailurePropagatesWithoutAnotherClaim() {
        var port = new EventFake(); port.claim = false;
        assertThatThrownBy(() -> new ProcessOrderReservationEventUseCase().process(event("COMMIT", LEGACY, null, ""), port))
                .hasMessage("missing winner");
        var failed = new EventFake(); failed.failCommit = true;
        assertThatThrownBy(() -> new ProcessOrderReservationEventUseCase().process(event("COMMIT", LEGACY, null, ""), failed))
                .hasMessage("commit failed");
        assertThat(failed.calls).containsExactly("claim", "COMMITlegacy");
    }
    static class EventFake implements OrderReservationEventPort {
        final List<String> calls = new ArrayList<>();
        boolean claim = true, failCommit; String state = "COMMITTED"; UUID operatedId;
        OrderReservationEventPolicy.Receipt receipt;
        public boolean claim(PromotionCommands.OrderEvent event) { calls.add("claim"); return claim; }
        public OrderReservationEventPolicy.Receipt existing(UUID eventId) {
            calls.add("existing"); if (receipt == null) throw new IllegalStateException("missing winner"); return receipt;
        }
        public String commit(UUID id, Long orderId, boolean bulk) {
            calls.add("COMMIT" + (bulk ? "bulk" : "legacy")); operatedId = id;
            if (failCommit) throw new IllegalStateException("commit failed"); return state;
        }
        public void release(UUID id, Long orderId, boolean bulk) { calls.add("RELEASE" + (bulk ? "bulk" : "legacy")); operatedId = id; }
        public RuntimeException conflict(String message) { return new IllegalStateException(message); }
    }
}
