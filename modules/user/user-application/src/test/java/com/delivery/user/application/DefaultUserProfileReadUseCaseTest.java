package com.delivery.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.user.application.api.UserProfileReadPort;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user.application.api.UserStatisticsResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultUserProfileReadUseCaseTest {

    private final RecordingProfileReadPort port = new RecordingProfileReadPort();
    private final DefaultUserProfileReadUseCase useCase = new DefaultUserProfileReadUseCase(port);

    @Test
    void delegatesProfileReadsAndAdministrativeQueries() {
        UserProfileResult result = result();
        UserStatisticsResult statistics = new UserStatisticsResult(10L, 7L, 1L, 1L, 1L, 9L, 1L);
        port.profile = result;
        port.statistics = statistics;
        port.all = List.of(result);

        assertThat(useCase.byAuthId(42L)).isSameAs(result);
        assertThat(useCase.byPrincipalId(42L)).isSameAs(result);
        assertThat(useCase.statistics()).isSameAs(statistics);
        assertThat(useCase.all()).containsExactly(result);
        assertThat(port.authId).isEqualTo(42L);
        assertThat(port.principalId).isEqualTo(42L);
    }

    @Test
    void rejectsMissingLookupKeysBeforeCallingThePort() {
        assertThatThrownBy(() -> useCase.byAuthId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("authId");
        assertThatThrownBy(() -> useCase.byPrincipalId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("principalId");
    }

    private UserProfileResult result() {
        return new UserProfileResult(7L, 42L, 42L, "ACTIVE", 0L,
                "user@example.com", "USER", "Customer", null, null, null, null,
                true, false, null, null, null, null, null);
    }

    private static final class RecordingProfileReadPort implements UserProfileReadPort {
        private Long authId;
        private Long principalId;
        private UserProfileResult profile;
        private UserStatisticsResult statistics;
        private List<UserProfileResult> all;

        @Override
        public UserProfileResult byAuthId(Long authId) {
            this.authId = authId;
            return profile;
        }

        @Override
        public UserProfileResult byPrincipalId(Long principalId) {
            this.principalId = principalId;
            return profile;
        }

        @Override
        public UserStatisticsResult statistics() {
            return statistics;
        }

        @Override
        public List<UserProfileResult> all() {
            return all;
        }
    }
}
