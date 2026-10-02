package com.delivery.user.application.api;

/** Application boundary for registration from Auth's signed handoff. */
public interface UserRegistrationUseCase {

    UserProfileResult register(RegisterUserCommand command);
}
