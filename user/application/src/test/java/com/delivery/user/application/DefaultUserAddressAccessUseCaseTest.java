package com.delivery.user.application;

import com.delivery.user.application.api.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultUserAddressAccessUseCaseTest {
    private final Reads reads = new Reads();
    private final DefaultUserAddressAccessUseCase access = new DefaultUserAddressAccessUseCase(reads);

    @Test void adminBypassesProfileLookupButAnonymousAndNonCustomerCannotAccess() {
        assertThat(access.canAccess(7L, null)).isFalse();
        assertThat(access.canAccess(null, new UserActor(null, false, true))).isTrue();
        assertThat(access.canAccess(7L, new UserActor(1L, false, false))).isFalse();
        assertThat(access.canAccess(7L, new UserActor(null, true, false))).isFalse();
        assertThat(access.canAccess(null, new UserActor(1L, true, false))).isFalse();
        assertThat(reads.calls).isZero();
        assertThatThrownBy(() -> new DefaultUserAddressAccessUseCase(null)).isInstanceOf(NullPointerException.class);
    }

    @Test void ownershipUsesLocalProfileIdInsteadOfAuthPrincipalId() {
        assertThat(access.canAccess(7L, new UserActor(42L, true, false))).isTrue();
        assertThat(access.canAccess(42L, new UserActor(42L, true, false))).isFalse();
        assertThat(reads.calls).isEqualTo(2);
        assertThat(reads.principal).isEqualTo(42L);
    }

    private static final class Reads implements UserProfileReadUseCase {
        int calls;
        Long principal;
        @Override public UserProfileResult byPrincipalId(Long id) {
            calls++; principal = id;
            return new UserProfileResult(7L, id, id, "ACTIVE", 0L, "user@example.test", "USER",
                    null, null, null, null, null, true, false, null, null, null, null, null);
        }
        @Override public UserProfileResult byAuthId(Long id) { throw new AssertionError("unexpected auth lookup"); }
        @Override public UserStatisticsResult statistics() { throw new AssertionError("unexpected statistics"); }
        @Override public List<UserProfileResult> all() { throw new AssertionError("unexpected list"); }
    }
}
