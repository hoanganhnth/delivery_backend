package com.delivery.notification.application;

import com.delivery.notification.application.api.PreferencePort;
import com.delivery.notification.domain.NotificationLifecycle;
import com.delivery.notification.domain.NotificationPreferences;
import java.time.LocalDateTime;

public final class Preferences {
    public record Result(NotificationPreferences preferences, LocalDateTime updatedAt) {}
    private final PreferencePort port;
    public Preferences(PreferencePort port) { this.port = port; }

    public Result get(Long principalId) {
        NotificationLifecycle.requirePositiveId(principalId, "principalId");
        return port.find(principalId).map(Preferences::result)
                .orElseGet(() -> new Result(NotificationPreferences.fromStoredMarketing(null), null));
    }

    public Result update(Long principalId, boolean enabled) {
        NotificationLifecycle.requirePositiveId(principalId, "principalId");
        if (port.upsert(principalId, enabled) != 1) throw new IllegalStateException(
                "notification preference update did not affect one principal");
        return result(port.find(principalId).orElseThrow(() -> new IllegalStateException(
                "notification preference upsert resolved without a committed row")));
    }

    private static Result result(PreferencePort.Stored stored) {
        return new Result(NotificationPreferences.fromStoredMarketing(stored.marketingEnabled()), stored.updatedAt());
    }
}
