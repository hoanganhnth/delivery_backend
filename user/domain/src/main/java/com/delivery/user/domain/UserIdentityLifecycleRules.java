package com.delivery.user.domain;

/** First Auth snapshot may start above one; initialized projections must not skip history. */
public final class UserIdentityLifecycleRules {
    private UserIdentityLifecycleRules() {}

    public static boolean shouldApply(Long storedVersion, long incomingVersion) {
        long current = storedVersion == null ? 0L : storedVersion;
        if (current > 0 && incomingVersion > current + 1) {
            throw new IllegalStateException("Identity lifecycle version gap");
        }
        return incomingVersion > current;
    }
}
