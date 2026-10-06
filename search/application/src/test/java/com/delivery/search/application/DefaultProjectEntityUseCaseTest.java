package com.delivery.search.application;

import com.delivery.search.application.api.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DefaultProjectEntityUseCaseTest {
    private ProjectionInput input(String action) {
        return new ProjectionInput(UUID.randomUUID(), LocalDateTime.of(2026, 9, 30, 10, 0),
                "dish", action, "1", Map.of(), 1L, null, null);
    }
    @Test void applyReplayAndNullClaimWriteWhileStaleStops() {
        for (ProjectionPorts.Claim claim : new ProjectionPorts.Claim[]{ProjectionPorts.Claim.APPLY,
                ProjectionPorts.Claim.EXACT_REPLAY, null, ProjectionPorts.Claim.STALE}) {
            Ports ports = new Ports(); ports.claim = claim;
            new DefaultProjectEntityUseCase(ports).project(input("UPDATE"));
            assertEquals(claim == ProjectionPorts.Claim.STALE ? List.of("fingerprint", "claim", "stale")
                    : List.of("fingerprint", "claim", "received", "write"), ports.calls);
        }
    }
    @Test void deleteCountsOnlySuccessfulWriteAndFailurePreservesCause() {
        Ports ports = new Ports();
        new DefaultProjectEntityUseCase(ports).project(input("delete"));
        assertEquals(List.of("fingerprint", "claim", "received", "write", "tombstone"), ports.calls);
        ports.calls.clear(); ports.failureAt = "write";
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new DefaultProjectEntityUseCase(ports).project(input("DELETE")));
        assertEquals("Failed to synchronize search entity", failure.getMessage());
        assertSame(ports.failure, failure.getCause());
        assertEquals(List.of("fingerprint", "claim", "received", "write", "failed"), ports.calls);
    }
    @Test void admissionBeforeFingerprintAndClaimFailuresStayUnwrapped() {
        Ports ports = new Ports();
        assertThrows(IllegalArgumentException.class, () -> new DefaultProjectEntityUseCase(ports).project(null));
        assertThrows(IllegalArgumentException.class, () -> new DefaultProjectEntityUseCase(ports).project(input("bad")));
        assertTrue(ports.calls.isEmpty());
        for (String at : List.of("fingerprint", "claim", "received")) {
            ports.calls.clear(); ports.failureAt = at;
            assertSame(ports.failure, assertThrows(RuntimeException.class,
                    () -> new DefaultProjectEntityUseCase(ports).project(input("CREATE"))));
            assertFalse(ports.calls.contains("write")); assertFalse(ports.calls.contains("failed"));
        }
    }
    @Test void tombstoneTelemetryFailureRetainsOriginalCatchBoundary() {
        Ports ports = new Ports(); ports.failureAt = "tombstone";
        assertSame(ports.failure, assertThrows(IllegalStateException.class,
                () -> new DefaultProjectEntityUseCase(ports).project(input("DELETE"))).getCause());
        assertEquals(List.of("fingerprint", "claim", "received", "write", "tombstone", "failed"), ports.calls);
    }
    @Test void everyPortReceivesTheOriginalInputAndClaimReceivesItsFingerprint() {
        ProjectionInput input = input("CREATE");
        Ports ports = new Ports();
        ports.expectedInput = input;
        new DefaultProjectEntityUseCase(ports).project(input);
        ports.claim = ProjectionPorts.Claim.STALE;
        new DefaultProjectEntityUseCase(ports).project(input);
        ports.claim = ProjectionPorts.Claim.EXACT_REPLAY;
        ports.failureAt = "write";
        assertThrows(IllegalStateException.class, () -> new DefaultProjectEntityUseCase(ports).project(input));
    }
    @Test void staleAndFailureObserverExceptionsAreNotWrappedOrRetried() {
        Ports ports = new Ports();
        ports.claim = ProjectionPorts.Claim.STALE;
        ports.failureAt = "stale";
        Ports stalePorts = ports;
        assertSame(ports.failure, assertThrows(RuntimeException.class,
                () -> new DefaultProjectEntityUseCase(stalePorts).project(input("DELETE"))));
        assertEquals(List.of("fingerprint", "claim", "stale"), ports.calls);

        RuntimeException observerFailure = new RuntimeException("observer failed");
        ports = new Ports() {
            @Override public void replayFailed(ProjectionInput input, Exception failure) {
                super.replayFailed(input, failure);
                throw observerFailure;
            }
        };
        ports.failureAt = "write";
        Ports failingPorts = ports;
        assertSame(observerFailure, assertThrows(RuntimeException.class,
                () -> new DefaultProjectEntityUseCase(failingPorts).project(input("UPDATE"))));
        assertEquals(List.of("fingerprint", "claim", "received", "write", "failed"), ports.calls);
    }
    @Test void fatalWriterErrorsRemainOutsideTheExceptionCatchBoundary() {
        AssertionError fatal = new AssertionError("fatal");
        Ports ports = new Ports() {
            @Override public void write(ProjectionInput input) { call("write"); throw fatal; }
        };
        assertSame(fatal, assertThrows(AssertionError.class,
                () -> new DefaultProjectEntityUseCase(ports).project(input("DELETE"))));
        assertEquals(List.of("fingerprint", "claim", "received", "write"), ports.calls);
    }
    private static class Ports implements ProjectionPorts {
        final List<String> calls = new ArrayList<>();
        Claim claim = Claim.APPLY;
        String failureAt;
        RuntimeException failure = new RuntimeException("failure");
        ProjectionInput expectedInput;
        void checkInput(ProjectionInput input) { if (expectedInput != null) assertSame(expectedInput, input); }
        void call(String name) { calls.add(name); if (name.equals(failureAt)) throw failure; }
        public String fingerprint(ProjectionInput input) { checkInput(input); call("fingerprint"); return "fp"; }
        public Claim claim(ProjectionInput input, String fp) { checkInput(input); assertEquals("fp", fp); call("claim"); return claim; }
        public void write(ProjectionInput input) { checkInput(input); call("write"); }
        public void stale(ProjectionInput input) { checkInput(input); call("stale"); }
        public void received(ProjectionInput input) { checkInput(input); call("received"); }
        public void tombstoneApplied() { call("tombstone"); }
        public void replayFailed(ProjectionInput input, Exception e) { checkInput(input); assertSame(failure, e); call("failed"); }
    }
}
