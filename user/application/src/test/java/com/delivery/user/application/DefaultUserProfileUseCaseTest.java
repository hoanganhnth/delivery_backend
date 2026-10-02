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
    void rejectsDivergentAuthIdentityBeforePersistence() {
        CreateUserCommand command = new CreateUserCommand(
                7L, 8L, "user@example.com", "USER", null, null, null, null, null);
        assertThatThrownBy(() -> useCase.create(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("authId and principalId must identify the same Auth account");
        assertThat(port.created).isNull();
    }

    @Test
    void rejectsMissingProvisioningIdentityBeforePersistence() {
        CreateUserCommand command = new CreateUserCommand(
                7L, 7L, " ", "USER", null, null, null, null, null);
        assertThatThrownBy(() -> useCase.create(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("authId, principalId, email and role are required for user provisioning");
        assertThat(port.created).isNull();
    }

    @Test
    void verifiesExistingPrincipalAndEmailWithinThePersistenceCallback() {
        CreateUserCommand command = new CreateUserCommand(
                7L, 7L, "USER@example.com", "USER", null, null, null, null, null);
        port.createResult = result();
        useCase.create(command);
        port.check.existingPrincipal(result());
        port.check.existingEmail(result());
        UserProfileResult other = new UserProfileResult(12L, 8L, 8L, "ACTIVE", 0L,
                "user@example.com", "USER", null, null, null, null, null,
                true, false, null, null, null, null, null);
        assertThatThrownBy(() -> port.check.existingPrincipal(other))
                .isInstanceOf(com.delivery.user.domain.ProvisioningIdentityConflict.class)
                .hasMessage("principalId is already linked to a different user identity");
        assertThatThrownBy(() -> port.check.existingEmail(other))
                .isInstanceOf(com.delivery.user.domain.ProvisioningIdentityConflict.class)
                .hasMessage("email is already linked to a different auth identity");
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
        private com.delivery.user.application.api.UserProvisioningIdentityCheck check;
        private UpdateProfileCommand updated;
        private UserProfileResult createResult;
        private UserProfileResult updateResult;

        @Override
        public UserProfileResult create(CreateUserCommand command, com.delivery.user.application.api.UserProvisioningIdentityCheck check) {
            this.check = check;
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
