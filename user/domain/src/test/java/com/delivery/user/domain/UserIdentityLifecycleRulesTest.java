package com.delivery.user.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserIdentityLifecycleRulesTest {
    @Test void firstSnapshotMayBeLaterButInitializedProjectionCannotSkipHistory() {
        assertTrue(UserIdentityLifecycleRules.shouldApply(null, 5));
        assertTrue(UserIdentityLifecycleRules.shouldApply(0L, 5));
        assertTrue(UserIdentityLifecycleRules.shouldApply(5L, 6));
        assertFalse(UserIdentityLifecycleRules.shouldApply(5L, 5));
        assertFalse(UserIdentityLifecycleRules.shouldApply(5L, 4));
        assertThrows(IllegalStateException.class, () -> UserIdentityLifecycleRules.shouldApply(5L, 7));
    }
}
