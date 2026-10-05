package com.delivery.notification.application;

import com.delivery.notification.application.api.*;
import com.delivery.notification.domain.ReplayPayload;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DurableSendTest {
    private static final ReplayPayload PAYLOAD = new ReplayPayload(1L, 2L, "title", "message", "TYPE", "HIGH", 3L, "ORDER", "{}");
    static class Fixture implements DurableSendPort<StringBuilder> {
        List<String> calls = new ArrayList<>();
        StoredNotification<StringBuilder> existing;
        StoredNotification<StringBuilder> created = row("PENDING", PAYLOAD);
        RuntimeException createFailure, deliveryFailure;
        static StoredNotification<StringBuilder> row(String status, ReplayPayload payload) {
            return new StoredNotification<>(9L, payload, status, new StringBuilder(status == null ? "null" : status));
        }
        public Optional<StoredNotification<StringBuilder>> findByKey(String key) { calls.add("find:" + key); return Optional.ofNullable(existing); }
        public StoredNotification<StringBuilder> createCommitted(SendCommand command) { calls.add("commit"); if(createFailure != null) throw createFailure; return created; }
        public void deliver(SendCommand command, StoredNotification<StringBuilder> stored) { calls.add("deliver:" + stored.id() + ":" + command.sendPush()); if(deliveryFailure != null) throw deliveryFailure; }
        public void markResponseSent(StringBuilder result) { calls.add("response"); result.replace(0, result.length(), "SENT"); }
        StringBuilder send(String key, Boolean push) { return new DurableSend<>(this).send(new SendCommand(PAYLOAD, key, push)); }
    }
    @Test void invalidInputNeverTouchesPorts() {
        var f = new Fixture(); var useCase = new DurableSend<>(f);
        assertThrows(IllegalArgumentException.class, () -> useCase.send(null));
        assertThrows(IllegalArgumentException.class, () -> useCase.send(new SendCommand(null, "key", true)));
        assertThrows(IllegalArgumentException.class, () -> useCase.send(new SendCommand(new ReplayPayload(0L,null,null,null,null,null,null,null,null), "key", true)));
        assertTrue(f.calls.isEmpty());
    }
    @Test void unkeyedAndKeyedCreatesCommitBeforeDelivery() {
        for (String key : new String[]{null, "", " \t", " key "}) for(Boolean push : new Boolean[]{null, false, true}) {
            var f = new Fixture(); assertSame(f.created.response(), f.send(key, push));
            assertEquals("SENT", f.created.response().toString());
            var expected = new ArrayList<String>(); if(" key ".equals(key)) expected.add("find: key ");
            expected.addAll(List.of("commit", "deliver:9:" + push, "response")); assertEquals(expected, f.calls);
        }
    }
    @Test void allExistingNonPendingStatesAreNoOpsAndPendingReusesId() {
        for (String state : new String[]{null, "SENT", "FAILED", "READ", "DELIVERED", "", "pending", "PENDING"}) {
            var f = new Fixture(); f.existing = Fixture.row(state, PAYLOAD);
            assertSame(f.existing.response(), f.send("key", true));
            assertEquals("PENDING".equals(state) ? List.of("find:key", "deliver:9:true", "response") : List.of("find:key"), f.calls);
        }
    }
    @Test void contradictionIsRejectedOnBothLookupAndConcurrentClaim() {
        var mismatched = new ReplayPayload(99L,2L,"title","message","TYPE","HIGH",3L,"ORDER","{}");
        for(boolean lookup : new boolean[]{true,false}) {
            var f = new Fixture(); if(lookup) f.existing = Fixture.row("SENT", mismatched); else f.created = Fixture.row("PENDING", mismatched);
            assertEquals("Deduplication key is already bound to a different notification payload",
                    assertThrows(ReplayConflictException.class, () -> f.send("key", true)).getMessage());
            assertEquals(lookup ? List.of("find:key") : List.of("find:key", "commit"), f.calls);
        }
    }
    @Test void pendingReplayCanChangePushChoiceWithoutChangingReplayIdentity() {
        var f = new Fixture(); f.existing = Fixture.row("PENDING", PAYLOAD);
        f.deliveryFailure = new IllegalStateException("retry");
        assertThrows(IllegalStateException.class, () -> f.send("key", false));
        f.deliveryFailure = null;
        assertSame(f.existing.response(), f.send("key", true));
        assertEquals(List.of("find:key", "deliver:9:false", "find:key", "deliver:9:true", "response"), f.calls);
    }
    @Test void concurrentSentClaimStillPassesThroughCoordinator() {
        var f = new Fixture(); f.created = Fixture.row("SENT", PAYLOAD); f.send("key", true);
        assertEquals(List.of("find:key", "commit", "deliver:9:true", "response"), f.calls);
    }
    @Test void storageAndPushFailuresPropagateWithoutResponseCompletion() {
        var f = new Fixture(); f.createFailure = new IllegalStateException("db");
        assertSame(f.createFailure, assertThrows(IllegalStateException.class, () -> f.send("key", true)));
        assertEquals(List.of("find:key", "commit"), f.calls);
        var failedPush = new Fixture(); failedPush.deliveryFailure = new IllegalStateException("push"); var finalF = failedPush;
        assertSame(failedPush.deliveryFailure, assertThrows(IllegalStateException.class, () -> finalF.send("key", true)));
        assertEquals("PENDING", failedPush.created.response().toString());
        assertEquals(List.of("find:key", "commit", "deliver:9:true"), failedPush.calls);
    }
}
