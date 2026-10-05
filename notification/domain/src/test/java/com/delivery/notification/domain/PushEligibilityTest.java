package com.delivery.notification.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PushEligibilityTest {
    @Test void onlyExplicitTrueRequestsPush() {
        assertFalse(PushEligibility.requested(null));
        assertFalse(PushEligibility.requested(false));
        assertTrue(PushEligibility.requested(true));
    }
    @Test void validationRetainsOrderMessagesAndWhitespace() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertEquals("userId must be positive", assertThrows(IllegalArgumentException.class,
                    () -> PushEligibility.validate(id, null, null)).getMessage());
            assertEquals("userId must be positive", assertThrows(IllegalArgumentException.class,
                    () -> PushEligibility.validateToken(id, null)).getMessage());
        }
        for (String blank : new String[]{null, "", " \t"}) {
            assertEquals("title is required", assertThrows(IllegalArgumentException.class,
                    () -> PushEligibility.validate(1L, blank, null)).getMessage());
            assertEquals("body is required", assertThrows(IllegalArgumentException.class,
                    () -> PushEligibility.validate(1L, "title", blank)).getMessage());
            assertEquals("fcmToken is required", assertThrows(IllegalArgumentException.class,
                    () -> PushEligibility.validateToken(1L, blank)).getMessage());
        }
        assertDoesNotThrow(() -> PushEligibility.validate(1L, " title ", " body "));
        assertDoesNotThrow(() -> PushEligibility.validateToken(1L, " token "));
    }
}
