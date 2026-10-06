package com.delivery.notification.application;

import com.delivery.notification.application.api.PreferenceAccessPort;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PreferenceAccessTest {
    static class Fixture implements PreferenceAccessPort<String> {
        List<String> calls = new ArrayList<>(); RuntimeException failure;
        public String get(Long id) { calls.add("get:"+id); if(failure != null) throw failure; return "stored"; }
        public String update(Long id, boolean enabled) { calls.add("update:"+id+":"+enabled); if(failure != null) throw failure; return "updated"; }
    }
    @Test void capabilityTruthTableAndDisabledUpdateDoNotReadRequestOrTouchPort() {
        for(boolean enabled : new boolean[]{false,true}) for(boolean present : new boolean[]{false,true}) {
            var f = new Fixture(); var access = new PreferenceAccess<>(enabled,present ? f : null);
            if(enabled && present) {
                assertEquals(new PreferenceAccess.Result<>(true,"stored"),access.get(71L));
                for(boolean marketing : new boolean[]{false,true}) assertEquals(new PreferenceAccess.Result<>(true,"updated"),access.update(71L,() -> marketing));
                assertEquals(List.of("get:71","update:71:false","update:71:true"),f.calls);
            } else {
                assertEquals(new PreferenceAccess.Result<>(false,null),access.get(71L));
                assertEquals(new PreferenceAccess.Result<>(false,null),access.update(71L,() -> {fail("disabled request read"); return true;}));
                assertTrue(f.calls.isEmpty());
            }
        }
    }
    @Test void serviceFailureAndEnabledMalformedRequestRetainFailures() {
        var f = new Fixture(); f.failure = new IllegalStateException("persistence"); var access = new PreferenceAccess<>(true,f);
        assertSame(f.failure,assertThrows(IllegalStateException.class,() -> access.get(71L)));
        assertSame(f.failure,assertThrows(IllegalStateException.class,() -> access.update(71L,() -> true)));
        assertThrows(NullPointerException.class,() -> access.update(71L,() -> null));
    }
}
