package com.delivery.user.application.api;

/** Persistence boundary for profile mutations; implemented by infrastructure. */
public interface UserProfilePort {

    UserProfileResult create(CreateUserCommand command);

    UserProfileResult update(UpdateProfileCommand command);
}
