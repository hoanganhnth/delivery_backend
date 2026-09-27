package com.delivery.user.application;

import com.delivery.user.application.api.CreateUserCommand;
import com.delivery.user.application.api.UpdateProfileCommand;
import com.delivery.user.application.api.UserProfilePort;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user.application.api.UserProfileUseCase;
import java.util.Objects;

/**
 * Coordinates profile commands without depending on persistence or transport
 * frameworks. The port owns idempotency, mapping, and durable side effects.
 */
public final class DefaultUserProfileUseCase implements UserProfileUseCase {

    private final UserProfilePort profilePort;

    public DefaultUserProfileUseCase(UserProfilePort profilePort) {
        this.profilePort = Objects.requireNonNull(profilePort, "profilePort");
    }

    @Override
    public UserProfileResult create(CreateUserCommand command) {
        return profilePort.create(Objects.requireNonNull(command, "command"));
    }

    @Override
    public UserProfileResult update(UpdateProfileCommand command) {
        return profilePort.update(Objects.requireNonNull(command, "command"));
    }
}
