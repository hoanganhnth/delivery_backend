package com.delivery.auth.application.api;

public interface LogoutUseCase {
    void logout(String rawRefreshToken);
}
