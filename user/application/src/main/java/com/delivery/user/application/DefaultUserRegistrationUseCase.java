package com.delivery.user.application;

import com.delivery.user.application.api.CreateUserCommand;
import com.delivery.user.application.api.ProvisioningIdentityPort;
import com.delivery.user.application.api.RegisterUserCommand;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user.application.api.UserProfileUseCase;
import com.delivery.user.application.api.UserRegistrationUseCase;
import java.util.Objects;

/** Derives immutable profile identity exclusively from Auth's signed handoff. */
public final class DefaultUserRegistrationUseCase implements UserRegistrationUseCase {
    private final ProvisioningIdentityPort identities;
    private final UserProfileUseCase profiles;

    public DefaultUserRegistrationUseCase(ProvisioningIdentityPort identities, UserProfileUseCase profiles) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    @Override
    public UserProfileResult register(RegisterUserCommand command) {
        Objects.requireNonNull(command, "command");
        var identity = identities.verify(command.provisioningToken());
        return profiles.create(new CreateUserCommand(identity.principalId(), identity.principalId(),
                identity.email(), identity.role(), command.fullName(), command.phone(), command.dob(),
                command.avatarUrl(), command.address()));
    }
}
