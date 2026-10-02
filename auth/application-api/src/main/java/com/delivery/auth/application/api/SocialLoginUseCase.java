package com.delivery.auth.application.api;

/** Application boundary for provider-backed login. */
public interface SocialLoginUseCase {

    AuthenticationResult login(SocialLoginCommand command);
}
