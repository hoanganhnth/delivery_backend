package com.delivery.user.application;

import com.delivery.user.application.api.CreateUserCommand;
import com.delivery.user.application.api.UpdateProfileCommand;
import com.delivery.user.application.api.UserProfilePort;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user.application.api.UserProfileUseCase;
import java.util.Objects;
import com.delivery.user.application.api.UserProvisioningIdentityCheck;
import com.delivery.user.domain.UserProvisioningRules;

/**
 * Coordinates profile commands without depending on persistence or transport
 * frameworks. Persistence executes core identity checks inside its transaction.
 */
public final class DefaultUserProfileUseCase implements UserProfileUseCase {

    private final UserProfilePort profilePort;

    public DefaultUserProfileUseCase(UserProfilePort profilePort) {
        this.profilePort = Objects.requireNonNull(profilePort, "profilePort");
    }

    @Override
    public UserProfileResult create(CreateUserCommand command) {
        Objects.requireNonNull(command, "command");
        UserProvisioningRules.validate(command.authId(), command.principalId(), command.email(), command.role());
        return profilePort.create(command, new UserProvisioningIdentityCheck() {
            @Override
            public void existingPrincipal(UserProfileResult existing) {
                UserProvisioningRules.requireSameIdentity(command.authId(), command.principalId(),
                        command.email(), command.role(), existing.authId(), existing.principalId(),
                        existing.email(), existing.role());
            }

            @Override
            public void existingEmail(UserProfileResult existing) {
                UserProvisioningRules.requireEmailOwner(command.principalId(), existing.principalId());
                existingPrincipal(existing);
            }
        });
    }

    @Override
    public UserProfileResult update(UpdateProfileCommand command) {
        return profilePort.update(Objects.requireNonNull(command, "command"));
    }
}
