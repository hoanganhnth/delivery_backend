package com.delivery.notification.domain;

/** Preference capability and defaults only; marketing dispatch is not implemented. */
public record NotificationPreferences(boolean transactionalNotificationsEnabled,
        boolean marketingNotificationsEnabled, boolean configured) {
    public static NotificationPreferences fromStoredMarketing(Boolean storedMarketing) {
        return new NotificationPreferences(true, Boolean.TRUE.equals(storedMarketing), storedMarketing != null);
    }

    public static boolean capabilityAvailable(boolean enabled, boolean servicePresent) {
        return enabled && servicePresent;
    }
}
