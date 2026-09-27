package com.delivery.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.user.application.api.CreateUserCommand;
import com.delivery.user.application.api.UpdateProfileCommand;
import com.delivery.user.application.api.UserProfilePort;
import com.delivery.user.application.api.UserProfileResult;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DefaultUserProfileUseCaseTest {

    private final RecordingProfilePort port = new RecordingProfilePort();
    private final DefaultUserProfileUseCase useCase = new DefaultUserProfileUseCase(port);

    @Test
    void createDelegatesTheTrustedCommandAndReturnsThePortResult() {
        CreateUserCommand command = new CreateUserCommand(
                7L, 7L, "user@example.com", "USER", "Customer", null,
                LocalDate.of(2000, 1, 1), null, null);
        UserProfileResult result = result();
        port.createResult = result;

        assertThat(useCase.create(command)).isSameAs(result);
        assertThat(port.created).isSameAs(command);
    }

    @Test
    void updateDelegatesTheProfilePatchAndReturnsThePortResult() {
        UpdateProfileCommand command = new UpdateProfileCommand(
                11L, "Updated", "0900000000", null, null, "District 1");
        UserProfileResult result = result();
        port.updateResult = result;

        assertThat(useCase.update(command)).isSameAs(result);
        assertThat(port.updated).isSameAs(command);
    }

    @Test
    void rejectsMissingCommandsBeforeCallingThePort() {
        assertThatThrownBy(() -> useCase.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
        assertThatThrownBy(() -> useCase.update(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
    }

    private UserProfileResult result() {
        return new UserProfileResult(11L, 7L, 7L, "ACTIVE", 0L,
                "user@example.com", "USER", "Customer", null, null, null, null,
                true, false, null, null, null, null, null);
    }

    private static final class RecordingProfilePort implements UserProfilePort {
        private CreateUserCommand created;
        private UpdateProfileCommand updated;
        private UserProfileResult createResult;
        private UserProfileResult updateResult;

        @Override
        public UserProfileResult create(CreateUserCommand command) {
            created = command;
            return createResult;
        }

        @Override
        public UserProfileResult update(UpdateProfileCommand command) {
            updated = command;
            return updateResult;
        }
    }
}
