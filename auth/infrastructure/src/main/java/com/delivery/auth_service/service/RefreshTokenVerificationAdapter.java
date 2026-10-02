package com.delivery.auth_service.service;

import com.delivery.auth.application.api.RefreshTokenVerificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenVerificationAdapter implements RefreshTokenVerificationPort {
    private final TokenService tokens;
    @Override public boolean isValid(String rawToken) { return tokens.isValidRefreshToken(rawToken); }
    @Override public String claimedFamily(String rawToken) { return tokens.extractRefreshTokenFamily(rawToken); }
}
