package com.delivery.settlement.domain.cod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CodHoldPolicyTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
    private CodHold held(LocalDateTime expiry) {
        return new CodHold(UUID.randomUUID(), UUID.randomUUID(), 1L, 2L, 22L, UUID.randomUUID(), null,
                BigDecimal.TEN, CodHold.Status.HELD, expiry, UUID.randomUUID(), "key", now, null, null, null);
    }
    @Test void replayRequiresHeldStateAndAnUnexpiredLease() {
        assertDoesNotThrow(() -> held(now.plusSeconds(1)).requireReusable(now));
        assertDoesNotThrow(() -> held(null).requireReusable(now));
        assertThrows(IllegalStateException.class, () -> held(now).requireReusable(now));
        assertThrows(IllegalStateException.class, () -> held(null).updated(CodHold.Status.COMMITTED, now, null, null).requireReusable(now));
    }
    @Test void heldCommitAtDeadlineExpiresWhileValidCommitKeepsReservation() {
        var expired = held(now).decideTransition(CodHold.Status.COMMITTED, now);
        assertEquals(CodHold.Status.EXPIRED, expired.target());
        assertTrue(expired.releaseReservation());
        var commit = held(now.plusSeconds(1)).decideTransition(CodHold.Status.COMMITTED, now);
        assertEquals(CodHold.Stamp.COMMITTED, commit.stamp());
        assertFalse(commit.releaseReservation());
        assertTrue(held(now).decideTransition(CodHold.Status.HELD, now).unchanged());
        assertThrows(IllegalStateException.class, () -> held(now).decideTransition(CodHold.Status.CONSUMED, now));
    }
    @Test void committedCanBeConsumedOrReleasedButCannotExpire() {
        var committed = held(now).updated(CodHold.Status.COMMITTED, now, null, null);
        assertTrue(committed.decideTransition(CodHold.Status.CONSUMED, now).releaseReservation());
        assertEquals(CodHold.Stamp.CONSUMED, committed.decideTransition(CodHold.Status.CONSUMED, now).stamp());
        assertEquals(CodHold.Stamp.RELEASED, committed.decideTransition(CodHold.Status.RELEASED, now).stamp());
        assertThrows(IllegalStateException.class, () -> committed.decideTransition(CodHold.Status.EXPIRED, now));
        assertThrows(IllegalStateException.class, () -> committed.decideTransition(CodHold.Status.HELD, now));
        assertEquals(CodHold.Status.RELEASED, held(null).decideTransition(CodHold.Status.RELEASED, now).target());
        assertEquals(CodHold.Status.EXPIRED, held(null).decideTransition(CodHold.Status.EXPIRED, now).target());
    }
    @Test void holdCommandKeepsTheStableSessionWaveOfferKeyAndExistingValidation() {
        var offer = new CodHoldCommand.Item(null, UUID.randomUUID(), 1L, 2L, BigDecimal.TEN, now);
        var command = new CodHoldCommand(UUID.randomUUID(), 22L, UUID.randomUUID(), null, List.of(offer));
        assertDoesNotThrow(command::validate);
        assertEquals(command.matchingSessionId() + ":null:" + offer.offerId(), command.idempotencyKey(offer));
        assertThrows(IllegalArgumentException.class, () -> new CodHoldCommand(null, 22L, UUID.randomUUID(), null, List.of(offer)).validate());
        assertThrows(IllegalArgumentException.class, () -> new CodHoldCommand(UUID.randomUUID(), 0L, UUID.randomUUID(), null, List.of(offer)).validate());
        assertThrows(IllegalArgumentException.class, () -> new CodHoldCommand(UUID.randomUUID(), 22L, null, null, List.of(offer)).validate());
        assertThrows(IllegalArgumentException.class, () -> new CodHoldCommand(UUID.randomUUID(), 22L, UUID.randomUUID(), null, List.of()).validate());
        assertThrows(IllegalArgumentException.class, () -> new CodHoldCommand(UUID.randomUUID(), 22L, UUID.randomUUID(), null, List.of(offer,offer,offer,offer)).validate());
    }    @Test void rejectsMissingItemFieldsAndNullRequestFieldsBeforeCapacityAccess() {
        var session = UUID.randomUUID();
        var offer = UUID.randomUUID();
        var event = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new CodHoldCommand(event, null, session, null, null).validate());
        assertThrows(IllegalArgumentException.class, () -> new CodHoldCommand(event, 22L, session, null, null).validate());
        var invalid = List.of(new CodHoldCommand.Item(null, null, 1L, 2L, BigDecimal.TEN, now),
                new CodHoldCommand.Item(null, offer, null, 2L, BigDecimal.TEN, now),
                new CodHoldCommand.Item(null, offer, 1L, null, BigDecimal.TEN, now),
                new CodHoldCommand.Item(null, offer, 1L, 2L, null, now),
                new CodHoldCommand.Item(null, offer, 1L, 2L, BigDecimal.ZERO, now),
                new CodHoldCommand.Item(null, offer, 1L, 2L, BigDecimal.TEN, null));
        for (var item : invalid) assertThrows(IllegalArgumentException.class,
                () -> new CodHoldCommand(event, 22L, session, null, List.of(item)).validate());
        var source = new java.util.ArrayList<CodHoldCommand.Item>();
        source.add(new CodHoldCommand.Item(null, offer, 1L, 2L, BigDecimal.TEN, now));
        var snapshot = new CodHoldCommand(event, 22L, session, null, source);
        source.clear();
        assertEquals(1, snapshot.offers().size());
    }

    @Test void legacyTerminalBranchIsPreservedPendingSeparatePolicyReview() {
        var terminal = held(now).updated(CodHold.Status.RELEASED, null, now, null);
        assertEquals(CodHold.Stamp.NONE, terminal.decideTransition(CodHold.Status.HELD, now).stamp());
        assertEquals(CodHold.Status.COMMITTED, held(null).decideTransition(CodHold.Status.COMMITTED, now).target());
    }

}
