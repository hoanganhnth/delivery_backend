package com.delivery.notification.application;

import com.delivery.notification.application.api.PushPort;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DispatchPushTest {
    static class Fixture implements PushPort {
        boolean configured = true;
        Set<Object> tokens = new LinkedHashSet<>(List.of("first", "second", "third"));
        List<String> calls = new ArrayList<>();
        Outcome outcome = Outcome.SENT;
        RuntimeException tokenFailure, sendFailure, removeFailure;
        String failToken;
        public boolean configured() { calls.add("configured"); return configured; }
        public Set<Object> tokens(Long id) { calls.add("tokens:"+id); if(tokenFailure != null) throw tokenFailure; return tokens; }
        public Outcome send(String token, String title, String body, Map<String,String> data, Long id) {
            assertEquals(" title ",title); assertEquals(" body ",body); assertEquals(Map.of("notificationId","9"),data);
            calls.add("send:"+token); if(token.equals(failToken)) throw sendFailure; return outcome;
        }
        public void removeToken(Long id,String token) { calls.add("remove:"+token); if(removeFailure != null) throw removeFailure; }
        void run() { new DispatchPush(this).send(7L," title "," body ",Map.of("notificationId","9")); }
    }
    @Test void invalidPushFailsBeforeCapabilityOrRedisIncludingUnconfiguredProvider() {
        var f = new Fixture(); f.configured = false;
        assertThrows(IllegalArgumentException.class, () -> new DispatchPush(f).send(null,null,null,null));
        assertThrows(IllegalArgumentException.class, () -> new DispatchPush(f).send(1L,"",null,null));
        assertThrows(IllegalArgumentException.class, () -> new DispatchPush(f).send(1L,"title","",null));
        assertTrue(f.calls.isEmpty());
    }
    @Test void absentProviderAndEmptyMembershipAreNoops() {
        var f = new Fixture(); f.configured = false; f.run(); assertEquals(List.of("configured"),f.calls);
        f = new Fixture(); f.tokens = Set.of(); f.run(); assertEquals(List.of("configured","tokens:7"),f.calls);
    }
    @Test void tokenIterationAndUnregisteredRemovalPreserveOrderAndObjectConversion() {
        var f = new Fixture(); f.run(); assertEquals(List.of("configured","tokens:7","send:first","send:second","send:third"),f.calls);
        f = new Fixture(); f.outcome = PushPort.Outcome.UNREGISTERED; f.tokens = Set.of(123); f.run();
        assertEquals(List.of("configured","tokens:7","send:123","remove:123"),f.calls);
    }
    @Test void redisAndCleanupFailuresPropagate() {
        var f = new Fixture(); f.tokenFailure = new IllegalStateException("redis");
        assertSame(f.tokenFailure,assertThrows(IllegalStateException.class,f::run));
        assertEquals(List.of("configured","tokens:7"),f.calls);
        f = new Fixture(); f.outcome = PushPort.Outcome.UNREGISTERED; f.removeFailure = new IllegalStateException("cleanup");
        assertSame(f.removeFailure,assertThrows(IllegalStateException.class,f::run));
        assertEquals(List.of("configured","tokens:7","send:first","remove:first"),f.calls);
    }
    @Test void partialFailureStopsIterationAndRetryRepeatsPreviouslySentTokens() {
        var f = new Fixture(); f.failToken = "second"; f.sendFailure = new IllegalStateException("provider");
        assertSame(f.sendFailure,assertThrows(IllegalStateException.class,f::run));
        assertEquals(List.of("configured","tokens:7","send:first","send:second"),f.calls);
        f.calls.clear(); f.failToken = null; f.run();
        assertEquals(List.of("configured","tokens:7","send:first","send:second","send:third"),f.calls);
    }
    @Test void nullMembershipElementRetainsFailureBeforeProviderSend() {
        var f = new Fixture(); f.tokens = new HashSet<>(Collections.singleton(null));
        assertThrows(NullPointerException.class,f::run); assertEquals(List.of("configured","tokens:7"),f.calls);
    }
}
