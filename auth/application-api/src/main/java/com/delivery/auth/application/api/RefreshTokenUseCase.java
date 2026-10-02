package com.delivery.auth.application.api;

/** Application boundary for refresh-token rotation. */
public interface RefreshTokenUseCase {

    AuthenticationResult refresh(RefreshTokenCommand command);
}
