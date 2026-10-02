package com.delivery.auth.domain.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure token validity policy. Signing, parsing, key selection, and revocation
 * persistence remain infrastructure responsibilities.
 */
public final class TokenValidityRules {

    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    public static final Duration PROVISIONING_TOKEN_TTL = Duration.ofMinutes(15);

    private TokenValidityRules() {
    }

    public static boolean isValid(TokenValidity token, TokenType expectedType, Instant now) {
        return token != null
                && expectedType != null
                && expectedType == token.tokenType()
                && token.isValidAt(now);
    }

    public static boolean isAccessTokenValid(TokenValidity token, Instant now) {
        return isValid(token, TokenType.ACCESS, now);
    }

    public static boolean isRefreshTokenValid(
            TokenValidity token, String expectedTokenFamilyId, Instant now) {
        return isValid(token, TokenType.REFRESH, now)
                && token.belongsToFamily(expectedTokenFamilyId);
    }

    public static boolean isProvisioningTokenValid(TokenValidity token, Instant now) {
        return isValid(token, TokenType.PROVISIONING, now);
    }
}
