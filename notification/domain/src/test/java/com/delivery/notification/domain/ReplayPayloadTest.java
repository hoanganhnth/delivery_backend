package com.delivery.notification.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayPayloadTest {
    @Test
    void exactComparisonIncludesEveryImmutableFieldAndAllowsNullableFields() {
        Object[] values = {7L, 71L, "title", "message", "type", "HIGH", 9L, "ORDER", "{}"};
        ReplayPayload stored = payload(values);
        assertTrue(stored.matches(payload(values.clone())));
        assertFalse(stored.matches(null));
        for (int i = 0; i < values.length; i++) {
            Object[] changed = values.clone();
            changed[i] = null;
            assertFalse(stored.matches(payload(changed)), "field " + i);
            assertTrue(payload(changed).matches(payload(changed.clone())));
            changed[i] = values[i] instanceof Long ? 99L : "different";
            assertFalse(stored.matches(payload(changed)), "field " + i);
        }
        assertTrue(payload(new Object[9]).matches(payload(new Object[9])));
    }

    private ReplayPayload payload(Object[] v) {
        return new ReplayPayload((Long) v[0], (Long) v[1], (String) v[2], (String) v[3],
                (String) v[4], (String) v[5], (Long) v[6], (String) v[7], (String) v[8]);
    }
}
