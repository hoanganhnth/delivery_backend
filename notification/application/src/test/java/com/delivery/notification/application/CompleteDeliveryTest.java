package com.delivery.notification.application;

import com.delivery.notification.application.api.*;
import com.delivery.notification.domain.ReplayPayload;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompleteDeliveryTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);
    static class Fixture implements DeliveryPort {
        List<String> calls = new ArrayList<>();
        StoredNotification<Void> locked;
        Map<String,String> data;
        RuntimeException pushFailure, saveFailure;
        Fixture(String state) { locked = new StoredNotification<>(9L, new ReplayPayload(1L,null,"title","message","TYPE","HIGH",7L,"ORDER",null), state, null); }
        public Optional<StoredNotification<Void>> lock(Long id) { calls.add("lock:" + id); return Optional.ofNullable(locked); }
        public void push(Long user, String title, String message, Map<String,String> data) {
            assertEquals(1L,user); assertEquals("title",title); assertEquals("message",message);
            this.data = data; calls.add("push"); if(pushFailure != null) throw pushFailure;
        }
        public void saveSent(Long id, LocalDateTime at) { assertEquals(NOW,at); calls.add("save:" + id); if(saveFailure != null) throw saveFailure; }
        void deliver(Boolean push) { new CompleteDelivery(this, () -> {calls.add("clock"); return NOW;}).deliver(9L, push); }
    }
    @Test void lockedSentSkipsPushClockAndSave() {
        var f = new Fixture("SENT"); f.deliver(true); assertEquals(List.of("lock:9"),f.calls);
    }
    @Test void missingAndInvalidRowsStopBeforeExternalIO() {
        var f = new Fixture("PENDING"); f.locked = null;
        assertEquals("Notification not found: 9",assertThrows(IllegalStateException.class, () -> f.deliver(true)).getMessage());
        assertEquals(List.of("lock:9"),f.calls);
        for(String state : new String[]{null,"FAILED","READ","DELIVERED","pending"}) {
            var bad = new Fixture(state); assertThrows(IllegalStateException.class, () -> bad.deliver(true));
            assertEquals(List.of("lock:9"), bad.calls);
        }
    }
    @Test void pendingUsesLockedPayloadAndOnlyTrueSendsPush() {
        for(Boolean push : new Boolean[]{null,false,true}) {
            var f = new Fixture("PENDING"); f.deliver(push);
            assertEquals(Boolean.TRUE.equals(push) ? List.of("lock:9","push","clock","save:9") : List.of("lock:9","clock","save:9"), f.calls);
            if(Boolean.TRUE.equals(push)) assertEquals(Map.of("notificationId","9","type","TYPE","relatedEntityId","7","relatedEntityType","ORDER"),f.data);
        }
    }
    @Test void pushFailureStopsBeforeClockAndSaveAndSaveFailurePropagates() {
        var f = new Fixture("PENDING"); f.pushFailure = new IllegalStateException("provider");
        assertSame(f.pushFailure, assertThrows(IllegalStateException.class, () -> f.deliver(true)));
        assertEquals(List.of("lock:9","push"), f.calls);
        var failedSave = new Fixture("PENDING"); failedSave.saveFailure = new IllegalStateException("db");
        assertSame(failedSave.saveFailure, assertThrows(IllegalStateException.class, () -> failedSave.deliver(false)));
    }
}
