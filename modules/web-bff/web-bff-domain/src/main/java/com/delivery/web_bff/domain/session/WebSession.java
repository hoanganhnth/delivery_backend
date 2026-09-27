package com.delivery.web_bff.domain.session;

import java.time.Instant;

/** Persistent session state without ORM annotations. */
public final class WebSession {
    private final String sessionHash;
    private final long principalId;
    private final String email;
    private final String role;
    private final String csrfHash;
    private String accessTokenCipher;
    private String refreshTokenCipher;
    private String encryptionKeyVersion;
    private long generation;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant revokedAt;
    private Instant refreshClaimedUntil;
    private Instant updatedAt;

    public WebSession(String sessionHash, String accessTokenCipher, String refreshTokenCipher, String encryptionKeyVersion,
            long principalId, String email, String role, String csrfHash, long generation, Instant expiresAt, Instant createdAt) {
        if (sessionHash == null || sessionHash.isBlank() || accessTokenCipher == null || refreshTokenCipher == null
                || encryptionKeyVersion == null || email == null || role == null || csrfHash == null
                || expiresAt == null || createdAt == null || generation < 1) throw new IllegalArgumentException("Invalid web session");
        this.sessionHash = sessionHash; this.accessTokenCipher = accessTokenCipher; this.refreshTokenCipher = refreshTokenCipher;
        this.encryptionKeyVersion = encryptionKeyVersion; this.principalId = principalId; this.email = email; this.role = role;
        this.csrfHash = csrfHash; this.generation = generation; this.expiresAt = expiresAt; this.createdAt = createdAt; this.updatedAt = createdAt;
    }
    public String sessionHash() { return sessionHash; }
    public String accessTokenCipher() { return accessTokenCipher; }
    public String refreshTokenCipher() { return refreshTokenCipher; }
    public String encryptionKeyVersion() { return encryptionKeyVersion; }
    public long principalId() { return principalId; }
    public String email() { return email; }
    public String role() { return role; }
    public String csrfHash() { return csrfHash; }
    public long generation() { return generation; }
    public Instant expiresAt() { return expiresAt; }
    public Instant createdAt() { return createdAt; }
    public Instant revokedAt() { return revokedAt; }
    public Instant refreshClaimedUntil() { return refreshClaimedUntil; }
    public boolean isActive(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public boolean verifiesCsrf(String rawCsrf) { return rawCsrf != null && Security.constantTimeEquals(csrfHash, Security.hash(rawCsrf)); }
    public void revoke(Instant now) { revokedAt = now; refreshClaimedUntil = null; generation++; updatedAt = now; }
    public void rotate(String accessCipher, String refreshCipher, String keyVersion, Instant now) {
        if (accessCipher == null || refreshCipher == null || keyVersion == null) throw new IllegalArgumentException("Token material is required");
        accessTokenCipher = accessCipher; refreshTokenCipher = refreshCipher; encryptionKeyVersion = keyVersion;
        refreshClaimedUntil = null; generation++; updatedAt = now;
    }
    public boolean canClaimRefresh(long expectedGeneration, Instant now) {
        return isActive(now) && generation == expectedGeneration && (refreshClaimedUntil == null || !refreshClaimedUntil.isAfter(now));
    }
    public boolean claimRefresh(long expectedGeneration, Instant now, Instant leaseUntil) {
        if (!canClaimRefresh(expectedGeneration, now)) return false;
        refreshClaimedUntil = leaseUntil; updatedAt = now; return true;
    }
}
