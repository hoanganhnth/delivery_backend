package com.delivery.notification.application;

import com.delivery.notification.application.api.PreferenceAccessPort;
import com.delivery.notification.domain.NotificationPreferences;
import java.util.function.Supplier;

public final class PreferenceAccess<R> {
    public record Result<R>(boolean available, R response) {}
    private final boolean enabled;
    private final PreferenceAccessPort<R> port;
    public PreferenceAccess(boolean enabled, PreferenceAccessPort<R> port) {
        this.enabled = enabled;
        this.port = port;
    }

    public Result<R> get(Long principalId) {
        if (!available()) return new Result<>(false, null);
        return new Result<>(true, port.get(principalId));
    }

    public Result<R> update(Long principalId, Supplier<Boolean> marketingEnabled) {
        if (!available()) return new Result<>(false, null);
        return new Result<>(true, port.update(principalId, marketingEnabled.get()));
    }

    private boolean available() { return NotificationPreferences.capabilityAvailable(enabled, port != null); }
}
