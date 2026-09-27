package com.delivery.auth.domain.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SessionModelTest {

    @Test
    void sessionIsUsableOnlyBeforeExpiryAndWhenActive() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 27, 12, 0);
        Session session = new Session(
                1L, 7L, "phone-1", "Phone", Session.DeviceType.MOBILE,
                "127.0.0.1", "family-1", true, now, now.plusDays(7), now);

        assertTrue(session.isUsableAt(now));
        assertFalse(session.isUsableAt(now.plusDays(7)));
        assertTrue(session.belongsToAccount(7L));
        assertTrue(session.belongsToTokenFamily("family-1"));
        assertFalse(session.isUsableAt(null));
        assertFalse(session.belongsToAccount(null));
        assertFalse(session.belongsToAccount(8L));
        assertFalse(session.belongsToTokenFamily(null));
        assertFalse(session.belongsToTokenFamily("family-2"));
    }

    @Test
    void inactiveOrUnboundedSessionCannotBeUsed() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 27, 12, 0);
        Session inactive = new Session(
                1L, 7L, "phone-1", null, null, null, "family-1", false,
                null, now.plusDays(7), now);
        Session withoutExpiry = new Session(
                2L, 7L, "phone-2", null, null, null, "family-2", true,
                now, null, now);

        assertFalse(inactive.isUsableAt(now));
        assertFalse(withoutExpiry.isUsableAt(now));
    }
}
