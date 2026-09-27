package com.delivery.user.application.api;

/** Application boundary for user profile creation and updates. */
public interface UserProfileUseCase {

    UserProfileResult create(CreateUserCommand command);

    UserProfileResult update(UpdateProfileCommand command);
}
