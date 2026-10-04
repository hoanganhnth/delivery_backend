package com.delivery.dispatch.application;

import com.delivery.dispatch.application.api.DeliveryProgressUseCase.Outcome;
import com.delivery.dispatch.application.api.DispatchCase;
import com.delivery.dispatch.domain.CaseHistory;
import com.delivery.dispatch.domain.DispatchStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultDeliveryProgressUseCaseTest {

    /** In-memory case with a step list, mirroring the legacy history semantics. */
    static final class FakeCase implements DispatchCase {
        DispatchStatus status;
        boolean completed;
        final List<String[]> steps = new ArrayList<>();

        FakeCase(DispatchStatus status, String... steps) {
            this.status = status;
            for (int i = 0; i < steps.length; i += 2) this.steps.add(new String[] {steps[i], steps[i + 1]});
        }

        @Override public long orderId() { return 7; }
        @Override public DispatchStatus status() { return status; }
        @Override public void transitionTo(DispatchStatus status) { this.status = status; }
        @Override public void markCompleted() { completed = true; }
        @Override public void record(String stepName, String eventType, String eventData) {
            steps.add(new String[] {stepName, eventData});
        }
        @Override public CaseHistory history() {
            return new CaseHistory() {
                @Override public boolean has(String name) { return steps.stream().anyMatch(s -> s[0].equals(name)); }
                @Override public String latest(String name) {
                    String data = null;
                    for (String[] s : steps) if (s[0].equals(name) && s[1] != null) data = s[1];
                    return data;
                }
                @Override public String latestWithPrefix(String prefix) { throw new UnsupportedOperationException(); }
                @Override public Fact latestFact(String name) { throw new UnsupportedOperationException(); }
                @Override public long countWithPrefix(String prefix) { throw new UnsupportedOperationException(); }
                @Override public long count(String name) { throw new UnsupportedOperationException(); }
                @Override public List<Long> rejectingShippers() { throw new UnsupportedOperationException(); }
                @Override public List<Long> recordedRejectedShippers() { throw new UnsupportedOperationException(); }
                @Override public UUID currentMatchingSession() { throw new UnsupportedOperationException(); }
            };
        }
    }

    private final List<String> calls = new ArrayList<>();
    private final DefaultDeliveryProgressUseCase useCase = new DefaultDeliveryProgressUseCase(
            c -> calls.add("save:" + c.status()),
            (c, status, cause) -> calls.add("order:" + status),
            Objects::equals);

    @Test
    void forwardsStrictProgressAndCompletesOnDelivered() {
        FakeCase c = new FakeCase(DispatchStatus.SHIPPER_ASSIGNED);
        assertEquals(Outcome.APPLIED, useCase.apply(c, "PICKED_UP", "e1"));
        assertEquals(DispatchStatus.PICKING_UP, c.status);
        assertFalse(c.completed);
        assertEquals(Outcome.APPLIED, useCase.apply(c, "DELIVERING", "e2"));
        assertEquals(Outcome.APPLIED, useCase.apply(c, "DELIVERED", "e3"));
        assertEquals(DispatchStatus.COMPLETED, c.status);
        assertTrue(c.completed);
        assertEquals(List.of("save:PICKING_UP", "order:PICKED_UP", "save:DELIVERING", "order:DELIVERING",
                "save:COMPLETED", "order:DELIVERED"), calls);
    }

    @Test
    void cancellationBeforePickupCompletesAndIsForwarded() {
        FakeCase c = new FakeCase(DispatchStatus.SHIPPER_ASSIGNED);
        assertEquals(Outcome.APPLIED, useCase.apply(c, "CANCELLED", "e"));
        assertEquals(DispatchStatus.CANCELLED, c.status);
        assertTrue(c.completed);
        assertEquals(List.of("save:CANCELLED", "order:CANCELLED"), calls);
    }

    @Test
    void exactReplayIsSkippedAndContradictionFails() {
        FakeCase c = new FakeCase(DispatchStatus.PICKING_UP, "DELIVERY_PICKED_UP", "e1");
        assertEquals(Outcome.REPLAY, useCase.apply(c, "PICKED_UP", "e1"));
        assertThrows(IllegalStateException.class, () -> useCase.apply(c, "PICKED_UP", "e2"));
        assertTrue(calls.isEmpty());
    }

    @Test
    void cancellationConfirmsCompensationWithoutSecondOrderCommand() {
        FakeCase compensating = new FakeCase(DispatchStatus.COMPENSATING, "ORDER_CANCELLED", "c");
        assertEquals(Outcome.CANCELLATION_CONFIRMED, useCase.apply(compensating, "CANCELLED", "e"));
        assertEquals(DispatchStatus.CANCELLED, compensating.status);
        assertTrue(compensating.completed);
        FakeCase failed = new FakeCase(DispatchStatus.FAILED);
        assertEquals(Outcome.CANCELLATION_CONFIRMED, useCase.apply(failed, "CANCELLED", "e"));
        assertEquals(DispatchStatus.FAILED, failed.status);
        assertFalse(failed.completed);
        assertEquals(List.of("save:CANCELLED", "save:FAILED"), calls);
    }

    @Test
    void terminalAndOutOfOrderStatusesFail() {
        assertEquals("Terminal saga COMPLETED cannot apply delivery status PICKED_UP for orderId=7",
                assertThrows(IllegalStateException.class,
                        () -> useCase.apply(new FakeCase(DispatchStatus.COMPLETED), "PICKED_UP", "e")).getMessage());
        assertThrows(IllegalStateException.class,
                () -> useCase.apply(new FakeCase(DispatchStatus.COMPENSATING), "PICKED_UP", "e"));
        assertThrows(IllegalStateException.class,
                () -> useCase.apply(new FakeCase(DispatchStatus.FINDING_SHIPPER), "DELIVERED", "e"));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.apply(new FakeCase(DispatchStatus.SHIPPER_ASSIGNED), "LOST", "e"));
        assertTrue(calls.isEmpty());
    }

    @Test
    void shipperNotFoundEchoIsRecordedOnceAfterTheMatchOutcome() {
        FakeCase c = new FakeCase(DispatchStatus.FAILED, "SHIPPER_NOT_FOUND", "nf");
        assertEquals(Outcome.SHIPPER_NOT_FOUND_ECHO_RECORDED, useCase.apply(c, "SHIPPER_NOT_FOUND", "echo"));
        assertEquals(Outcome.REPLAY, useCase.apply(c, "SHIPPER_NOT_FOUND", "echo"));
        assertThrows(IllegalStateException.class, () -> useCase.apply(c, "SHIPPER_NOT_FOUND", "other"));
        assertEquals(List.of("save:FAILED"), calls);
        assertThrows(IllegalStateException.class, () -> useCase.apply(
                new FakeCase(DispatchStatus.FAILED), "SHIPPER_NOT_FOUND", "echo"));
        assertThrows(IllegalStateException.class, () -> useCase.apply(
                new FakeCase(DispatchStatus.FINDING_SHIPPER, "SHIPPER_NOT_FOUND", "nf"), "SHIPPER_NOT_FOUND", "echo"));
    }

    @Test
    void requiresAllCollaborators() {
        assertThrows(NullPointerException.class, () -> new DefaultDeliveryProgressUseCase(null, (c, s, e) -> { }, Objects::equals));
        assertThrows(NullPointerException.class, () -> new DefaultDeliveryProgressUseCase(c -> { }, null, Objects::equals));
        assertThrows(NullPointerException.class, () -> new DefaultDeliveryProgressUseCase(c -> { }, (c, s, e) -> { }, null));
    }
}
