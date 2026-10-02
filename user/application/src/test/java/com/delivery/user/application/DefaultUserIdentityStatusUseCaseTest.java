package com.delivery.user.application;

import com.delivery.user.application.api.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultUserIdentityStatusUseCaseTest {
    private UserIdentityProjection stored = new UserIdentityProjection("ACTIVE", 0L, false, true, null, null, "prior reason");
    private final DefaultUserIdentityStatusUseCase useCase = new DefaultUserIdentityStatusUseCase(
            (principal, transition) -> stored = transition.apply(stored));

    @Test void blockAndUnblockProjectFlagsAndMetadataAndIgnoreReplay() {
        useCase.apply(new ApplyUserIdentityStatusCommand(7L, "BLOCKED", 5, 1L));
        assertThat(stored.status()).isEqualTo("BLOCKED");
        assertThat(stored.version()).isEqualTo(5);
        assertThat(stored.blocked()).isTrue();
        assertThat(stored.active()).isFalse();
        assertThat(stored.blockedAt()).isNotNull();
        assertThat(stored.blockedBy()).isEqualTo(1L);
        assertThat(stored.reason()).isEqualTo("prior reason");
        var blocked = stored;
        useCase.apply(new ApplyUserIdentityStatusCommand(7L, "ACTIVE", 5, 2L));
        assertThat(stored).isSameAs(blocked);
        assertThatThrownBy(() -> useCase.apply(new ApplyUserIdentityStatusCommand(7L, "ACTIVE", 7, 2L)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Identity lifecycle version gap");
        assertThat(stored).isSameAs(blocked);
        useCase.apply(new ApplyUserIdentityStatusCommand(7L, "ACTIVE", 6, 2L));
        assertThat(stored.blocked()).isFalse();
        assertThat(stored.active()).isTrue();
        assertThat(stored.blockedAt()).isNull();
        assertThat(stored.blockedBy()).isNull();
        assertThat(stored.reason()).isNull();
    }

    @Test void missingProjectionIsIgnoredAndNullInputsFailBeforeMutation() {
        var absent = new DefaultUserIdentityStatusUseCase((principal, transition) -> {});
        absent.apply(new ApplyUserIdentityStatusCommand(7L, "ACTIVE", 1, null));
        assertThatThrownBy(() -> absent.apply(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultUserIdentityStatusUseCase(null)).isInstanceOf(NullPointerException.class);
    }
}
