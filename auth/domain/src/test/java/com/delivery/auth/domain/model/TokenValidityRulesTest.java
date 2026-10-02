package com.delivery.auth.domain.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TokenValidityRulesTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-09-27T05:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-27T05:05:00Z");

    @Test
    void accessAndRefreshTokensUseTheirExpectedTypesAndExpiryWindow() {
        TokenValidity access = new TokenValidity(
                TokenType.ACCESS, "7", null, ISSUED_AT, NOW.plusSeconds(60), false);
        TokenValidity refresh = new TokenValidity(
                TokenType.REFRESH, "7", "family-1", ISSUED_AT,
                NOW.plus(TokenValidityRules.REFRESH_TOKEN_TTL), false);

        assertTrue(TokenValidityRules.isAccessTokenValid(access, NOW));
        assertTrue(TokenValidityRules.isRefreshTokenValid(refresh, "family-1", NOW));
        assertFalse(TokenValidityRules.isRefreshTokenValid(refresh, "family-2", NOW));
        assertFalse(TokenValidityRules.isValid(access, TokenType.REFRESH, NOW));
    }

    @Test
    void revokedOrExpiredTokensFailClosed() {
        TokenValidity revoked = new TokenValidity(
                TokenType.ACCESS, "7", null, ISSUED_AT, NOW.plusSeconds(60), true);
        TokenValidity expired = new TokenValidity(
                TokenType.ACCESS, "7", null, ISSUED_AT, NOW, false);

        assertFalse(TokenValidityRules.isAccessTokenValid(revoked, NOW));
        assertFalse(TokenValidityRules.isAccessTokenValid(expired, NOW));
    }

    @Test
    void tokenValidityRejectsMissingOrFutureTemporalFacts() {
        TokenValidity missingIssuedAt = new TokenValidity(
                TokenType.ACCESS, "7", null, null, NOW.plusSeconds(60), false);
        TokenValidity missingExpiry = new TokenValidity(
                TokenType.ACCESS, "7", null, ISSUED_AT, null, false);
        TokenValidity futureIssuedAt = new TokenValidity(
                TokenType.ACCESS, "7", null, NOW.plusSeconds(1), NOW.plusSeconds(60), false);

        assertFalse(missingIssuedAt.isValidAt(NOW));
        assertFalse(missingExpiry.isValidAt(NOW));
        assertFalse(futureIssuedAt.isValidAt(NOW));
        assertFalse(futureIssuedAt.isValidAt(null));
        assertTrue(missingExpiry.isExpiredAt(NOW));
        assertTrue(missingExpiry.isExpiredAt(null));
        assertFalse(futureIssuedAt.belongsToFamily("family-1"));
        assertFalse(futureIssuedAt.belongsToFamily(null));
    }

    @Test
    void tokenRulesCoverAllIssuedTokenKindsAndMissingExpectedFacts() {
        TokenValidity provisioning = new TokenValidity(
                TokenType.PROVISIONING, "7", null, ISSUED_AT,
                NOW.plusSeconds(60), false);
        TokenValidity refresh = new TokenValidity(
                TokenType.REFRESH, "7", "family-1", ISSUED_AT,
                NOW.plusSeconds(60), false);

        assertTrue(TokenValidityRules.isProvisioningTokenValid(provisioning, NOW));
        assertFalse(TokenValidityRules.isProvisioningTokenValid(null, NOW));
        assertFalse(TokenValidityRules.isValid(provisioning, null, NOW));
        assertFalse(TokenValidityRules.isValid(null, TokenType.ACCESS, NOW));
        assertFalse(TokenValidityRules.isRefreshTokenValid(refresh, null, NOW));
    }
}
