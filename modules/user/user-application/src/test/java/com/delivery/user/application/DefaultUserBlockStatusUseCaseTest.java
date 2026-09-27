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
        assertThat(port.command).isSameAs(command);
    }

    @Test
    void rejectsMissingCommandsBeforeCallingThePort() {
        assertThatThrownBy(() -> useCase.update(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
    }

    private static final class RecordingBlockStatusPort implements UserBlockStatusPort {
        private UpdateUserBlockStatusCommand command;
        private UserBlockStatusResult result;

        @Override
        public UserBlockStatusResult update(UpdateUserBlockStatusCommand command) {
            this.command = command;
            return result;
        }
    }
}
