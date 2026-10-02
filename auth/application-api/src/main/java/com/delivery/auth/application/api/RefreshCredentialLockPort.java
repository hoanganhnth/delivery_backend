package com.delivery.auth.application.api;

import java.util.Optional;
import java.util.function.Function;

public interface RefreshCredentialLockPort {
    <T> T withLocked(String rawToken, Function<Optional<LockedRefreshCredential>, T> operation);
}
