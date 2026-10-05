package com.delivery.notification.application;

import com.delivery.notification.application.api.PreferencePort;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PreferencesTest {
    static class Fixture implements PreferencePort {
        Optional<Stored> stored = Optional.empty(); int changed = 1;
        List<String> calls = new ArrayList<>();
        public Optional<Stored> find(Long id) { calls.add("find:"+id); return stored; }
        public int upsert(Long id, boolean enabled) { calls.add("upsert:"+id+":"+enabled); return changed; }
    }
    @Test void validationPrecedesAllPersistence() {
        var f = new Fixture(); var preferences = new Preferences(f);
        for(Long id : new Long[]{null,0L,-1L}) {
            assertEquals("principalId must be positive",assertThrows(IllegalArgumentException.class, () -> preferences.get(id)).getMessage());
            assertThrows(IllegalArgumentException.class, () -> preferences.update(id,true));
        }
        assertTrue(f.calls.isEmpty());
    }
    @Test void missingOptOutOptInAndTimestampArePreserved() {
        var f = new Fixture(); var preferences = new Preferences(f);
        var missing = preferences.get(1L); assertFalse(missing.preferences().configured());
        assertFalse(missing.preferences().marketingNotificationsEnabled()); assertTrue(missing.preferences().transactionalNotificationsEnabled()); assertNull(missing.updatedAt());
        var at = LocalDateTime.of(2026,10,5,12,0);
        for(boolean enabled : new boolean[]{false,true}) {
            f.stored = Optional.of(new PreferencePort.Stored(enabled,at));
            var result = preferences.get(1L); assertTrue(result.preferences().configured());
            assertEquals(enabled,result.preferences().marketingNotificationsEnabled()); assertTrue(result.preferences().transactionalNotificationsEnabled()); assertEquals(at,result.updatedAt());
            f.calls.clear(); assertEquals(result,preferences.update(1L,enabled));
            assertEquals(List.of("upsert:1:"+enabled,"find:1"),f.calls);
        }
    }
    @Test void incorrectRowCountsAndMissingRereadKeepExactFailures() {
        for(int changed : new int[]{-1,0,2}) {
            var f = new Fixture(); f.changed = changed;
            assertEquals("notification preference update did not affect one principal",assertThrows(IllegalStateException.class, () -> new Preferences(f).update(1L,true)).getMessage());
            assertEquals(List.of("upsert:1:true"),f.calls);
        }
        var f = new Fixture(); assertEquals("notification preference upsert resolved without a committed row",assertThrows(IllegalStateException.class, () -> new Preferences(f).update(1L,true)).getMessage());
    }
}
