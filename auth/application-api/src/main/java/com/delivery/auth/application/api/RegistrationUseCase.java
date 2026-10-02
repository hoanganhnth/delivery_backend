package com.delivery.auth.application.api;

/** Application boundary for public account registration. */
public interface RegistrationUseCase {

    RegistrationResult register(RegisterCommand command);
}
