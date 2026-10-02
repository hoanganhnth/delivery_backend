package com.delivery.auth.application.api;

public interface RefreshTokenVerificationPort {
    boolean isValid(String rawToken);
    String claimedFamily(String rawToken);
}
