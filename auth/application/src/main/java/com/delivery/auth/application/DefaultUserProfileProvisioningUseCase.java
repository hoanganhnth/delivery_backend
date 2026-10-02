package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import java.util.Objects;

/** Auth owns acceptance and identity binding; the adapter only exchanges HTTP. */
public final class DefaultUserProfileProvisioningUseCase implements UserProfileProvisioningUseCase {
    private final UserProfileProvisioningPort profiles;
    public DefaultUserProfileProvisioningUseCase(UserProfileProvisioningPort profiles) {
        this.profiles = Objects.requireNonNull(profiles);
    }
    @Override public AuthAccount provision(AuthAccount account) {
        Objects.requireNonNull(account, "account");
        var reply = profiles.provision(account);
        if (reply == null || reply.status() != 1 || reply.userId() == null) {
            throw new IllegalStateException("User service did not provision profile: "
                    + (reply == null ? "empty response" : reply.message()));
        }
        if (!account.id().equals(reply.authId()) || reply.email() == null
                || !account.email().equalsIgnoreCase(reply.email()) || !account.role().name().equals(reply.role())) {
            throw new IllegalStateException("User service returned a conflicting provisioning identity");
        }
        return account.withProvisionedProfile(reply.userId());
    }
}
