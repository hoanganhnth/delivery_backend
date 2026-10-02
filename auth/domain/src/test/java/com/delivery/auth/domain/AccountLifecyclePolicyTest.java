package com.delivery.auth.domain;
import com.delivery.auth.domain.policy.AccountLifecyclePolicy;
import com.delivery.auth.domain.model.AuthAccount.LifecycleStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class AccountLifecyclePolicyTest {
    @Test void preservesBlockedAndAllOnboardingStates() {
        assertEquals(LifecycleStatus.BLOCKED, AccountLifecyclePolicy.status(false, 7L, false, null));
        assertEquals(LifecycleStatus.BLOCKED, AccountLifecyclePolicy.status(null, 7L, false, null));
        assertEquals(LifecycleStatus.PENDING_PROFILE, AccountLifecyclePolicy.status(true, null, false, null));
        assertEquals(LifecycleStatus.PENDING_EMAIL_VERIFICATION, AccountLifecyclePolicy.status(true, 7L, true, null));
        assertEquals(LifecycleStatus.ACTIVE, AccountLifecyclePolicy.status(true, 7L, true, LocalDateTime.now()));
        assertEquals(LifecycleStatus.ACTIVE, AccountLifecyclePolicy.status(true, 7L, false, null));
        assertEquals(LifecycleStatus.ACTIVE, AccountLifecyclePolicy.status(true, 7L, null, null));
    }
}
