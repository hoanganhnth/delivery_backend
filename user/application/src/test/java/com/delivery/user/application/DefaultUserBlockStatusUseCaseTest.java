package com.delivery.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.user.application.api.UpdateUserBlockStatusCommand;
import com.delivery.user.application.api.UserBlockStatusPort;
import com.delivery.user.application.api.UserBlockStatusResult;
import org.junit.jupiter.api.Test;

class DefaultUserBlockStatusUseCaseTest {

    private final RecordingBlockStatusPort port = new RecordingBlockStatusPort();
    private final DefaultUserBlockStatusUseCase useCase = new DefaultUserBlockStatusUseCase(port);

    @Test
    void updateDelegatesTheAuthProjectionCommandAndReturnsThePortResult() {
        UpdateUserBlockStatusCommand command = new UpdateUserBlockStatusCommand(7L, 1L, true, "fraud review");
        UserBlockStatusResult result = new UserBlockStatusResult(7L, true, false, null, 1L, "fraud review");
        port.result = result;

        assertThat(useCase.update(command)).isSameAs(result);
        assertThat(port.userId).isEqualTo(command.userId());
    }

    @Test
    void blockDecisionIsAppliedByCoreAndDefaultsTheMissingReason() {
        port.result = new UserBlockStatusResult(7L, false, true, null, null, null);
        var result = useCase.update(new UpdateUserBlockStatusCommand(7L, 1L, true, null));
        assertThat(result.blocked()).isTrue();
        assertThat(result.active()).isFalse();
        assertThat(result.blockedAt()).isNotNull();
        assertThat(result.blockedBy()).isEqualTo(1L);
        assertThat(result.reason()).isEqualTo("No reason provided");
    }

    @Test
    void explicitReasonAndNullUnblockClearMetadataWhileReplayIsUnchanged() {
        port.result = new UserBlockStatusResult(7L, null, true, null, null, null);
        var blocked = useCase.update(new UpdateUserBlockStatusCommand(7L, 1L, true, "review"));
        assertThat(blocked.reason()).isEqualTo("review");
        port.result = blocked;
        assertThat(useCase.update(new UpdateUserBlockStatusCommand(7L, 2L, true, "retry"))).isSameAs(blocked);
        var unblocked = useCase.update(new UpdateUserBlockStatusCommand(7L, 2L, null, null));
        assertThat(unblocked.blocked()).isFalse();
        assertThat(unblocked.active()).isTrue();
        assertThat(unblocked.blockedAt()).isNull();
        assertThat(unblocked.blockedBy()).isNull();
        assertThat(unblocked.reason()).isNull();
        port.result = unblocked;
        assertThat(useCase.update(new UpdateUserBlockStatusCommand(7L, 2L, false, null))).isSameAs(unblocked);
    }

    @Test
    void rejectsMissingCommandsBeforeCallingThePort() {
        assertThatThrownBy(() -> useCase.update(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
    }

    private static final class RecordingBlockStatusPort implements UserBlockStatusPort {
        private Long userId;
        private UserBlockStatusResult result;

        @Override
        public UserBlockStatusResult mutate(Long userId,
                java.util.function.UnaryOperator<UserBlockStatusResult> transition) {
            this.userId = userId;
            return transition.apply(result);
        }
    }
}
