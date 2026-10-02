package com.delivery.user.application.api;

/** Persistence boundary for profile mutations; implemented by infrastructure. */
public interface UserProfilePort {

    UserProfileResult create(CreateUserCommand command, UserProvisioningIdentityCheck identityCheck);

    UserProfileResult update(UpdateProfileCommand command);
}
