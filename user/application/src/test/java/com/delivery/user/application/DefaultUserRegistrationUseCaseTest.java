package com.delivery.user.application;

import com.delivery.user.application.api.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultUserRegistrationUseCaseTest {
    private final UserProfileResult result = new UserProfileResult(7L, 11L, 11L, "ACTIVE", 0L,
            "user@example.com", "USER", "Customer Test", null, null, null, null, true, false,
            null, null, null, null, null);
    private CreateUserCommand persisted;
    private final UserProfileUseCase profiles = new UserProfileUseCase() {
        public UserProfileResult create(CreateUserCommand command) { persisted = command; return result; }
        public UserProfileResult update(UpdateProfileCommand command) { throw new UnsupportedOperationException(); }
    };

    @Test void derivesImmutableIdentityFromSignedHandoffBeforeProvisioning() {
        var registration = new DefaultUserRegistrationUseCase(token -> {
            assertThat(token).isEqualTo("signed-handoff");
            return new ProvisioningIdentity(11L, "user@example.com", "USER");
        }, profiles);
        assertThat(registration.register(command())).isSameAs(result);
        assertThat(persisted.authId()).isEqualTo(11L);
        assertThat(persisted.principalId()).isEqualTo(11L);
        assertThat(persisted.email()).isEqualTo("user@example.com");
        assertThat(persisted.role()).isEqualTo("USER");
        assertThat(persisted.fullName()).isEqualTo("Customer Test");
    }

    @Test void invalidHandoffCannotReachProfilePersistence() {
        var registration = new DefaultUserRegistrationUseCase(token -> { throw new IllegalArgumentException("fixture invalid handoff"); }, profiles);
        assertThatThrownBy(() -> registration.register(command())).isInstanceOf(IllegalArgumentException.class);
        assertThat(persisted).isNull();
    }

    @Test void missingCommandAndMissingPortsFailBeforePersistence() {
        var registration = new DefaultUserRegistrationUseCase(token -> null, profiles);
        assertThatThrownBy(() -> registration.register(null)).isInstanceOf(NullPointerException.class).hasMessage("command");
        assertThatThrownBy(() -> new DefaultUserRegistrationUseCase(null, profiles)).isInstanceOf(NullPointerException.class).hasMessage("identities");
        assertThatThrownBy(() -> new DefaultUserRegistrationUseCase(token -> null, null)).isInstanceOf(NullPointerException.class).hasMessage("profiles");
    }

    private RegisterUserCommand command() {
        return new RegisterUserCommand("signed-handoff", "Customer Test", null, null, null, null);
    }
}
