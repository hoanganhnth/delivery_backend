package com.delivery.auth.application.api;

/** Application boundary for password login and session creation. */
public interface LoginUseCase {

    AuthenticationResult login(LoginCommand command);
}
