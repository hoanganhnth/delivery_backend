package com.delivery.auth.domain.model;

import java.time.Instant;

/**
 * Parsed token facts needed by application rules; raw JWT/JWS details stay in
 * the infrastructure adapter.
 */
public record TokenValidity(
        TokenType tokenType,
        String subject,
        String tokenFamilyId,
        Instant issuedAt,
        Instant expiresAt,
        boolean revoked) {

    /** Validity includes a non-future issue time and an exclusive expiry. */
    public boolean isValidAt(Instant now) {
        return now != null
                && !revoked
                && issuedAt != null
                && expiresAt != null
                && !now.isBefore(issuedAt)
                && now.isBefore(expiresAt);
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt == null || now == null || !now.isBefore(expiresAt);
    }

    public boolean belongsToFamily(String expectedTokenFamilyId) {
        return expectedTokenFamilyId != null
                && expectedTokenFamilyId.equals(tokenFamilyId);
    }
}
