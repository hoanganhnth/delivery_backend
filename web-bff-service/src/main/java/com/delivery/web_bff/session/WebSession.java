package com.delivery.web_bff.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "web_sessions")
public class WebSession {
    @Id @Column(name = "session_hash", length = 64) private String sessionHash;
    @Column(name = "access_token_cipher", nullable = false, columnDefinition = "TEXT") private String accessTokenCipher;
    @Column(name = "refresh_token_cipher", nullable = false, columnDefinition = "TEXT") private String refreshTokenCipher;
    @Column(name = "encryption_key_version", nullable = false) private String encryptionKeyVersion;
    @Column(name = "principal_id", nullable = false) private Long principalId;
    @Column(nullable = false) private String email;
    @Column(nullable = false) private String role;
    @Column(name = "csrf_hash", nullable = false, length = 64) private String csrfHash;
    @Column(nullable = false) private long generation;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "refresh_claimed_until") private Instant refreshClaimedUntil;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected WebSession() { }

    public WebSession(String sessionHash, String accessTokenCipher, String refreshTokenCipher,
            String encryptionKeyVersion, Long principalId, String email, String role,
            String csrfHash, long generation, Instant expiresAt, Instant createdAt) {
        this.sessionHash = sessionHash; this.accessTokenCipher = accessTokenCipher;
        this.refreshTokenCipher = refreshTokenCipher; this.encryptionKeyVersion = encryptionKeyVersion;
        this.principalId = principalId; this.email = email; this.role = role; this.csrfHash = csrfHash;
        this.generation = generation; this.expiresAt = expiresAt; this.createdAt = createdAt; this.updatedAt = createdAt;
    }

    public String getSessionHash() { return sessionHash; }
    public String getAccessTokenCipher() { return accessTokenCipher; }
    public String getRefreshTokenCipher() { return refreshTokenCipher; }
    public String getEncryptionKeyVersion() { return encryptionKeyVersion; }
    public Long getPrincipalId() { return principalId; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getCsrfHash() { return csrfHash; }
    public long getGeneration() { return generation; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getRefreshClaimedUntil() { return refreshClaimedUntil; }
    public void revoke(Instant now) { revokedAt = now; updatedAt = now; generation++; }
    public void rotate(String accessCipher, String refreshCipher, String keyVersion, Instant now) {
        accessTokenCipher = accessCipher;
        refreshTokenCipher = refreshCipher;
        encryptionKeyVersion = keyVersion;
        refreshClaimedUntil = null;
        generation++;
        updatedAt = now;
    }
}
