package com.delivery.notification.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationPreferencesTest {
    @Test
    void capabilityRequiresFlagAndService() {
        assertFalse(NotificationPreferences.capabilityAvailable(false, false));
        assertFalse(NotificationPreferences.capabilityAvailable(false, true));
        assertFalse(NotificationPreferences.capabilityAvailable(true, false));
        assertTrue(NotificationPreferences.capabilityAvailable(true, true));
    }

    @Test
    void absenceAndOptOutNeverDisableTransactionalNotifications() {
        for (Boolean stored : new Boolean[]{null, false, true}) {
            var preference = NotificationPreferences.fromStoredMarketing(stored);
            assertTrue(preference.transactionalNotificationsEnabled());
            assertEquals(Boolean.TRUE.equals(stored), preference.marketingNotificationsEnabled());
            assertEquals(stored != null, preference.configured());
        }
    }
}
