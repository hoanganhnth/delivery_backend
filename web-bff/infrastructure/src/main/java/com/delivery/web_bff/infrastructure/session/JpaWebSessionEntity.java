package com.delivery.web_bff.infrastructure.session;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "web_sessions")
public class JpaWebSessionEntity {
    @Id private String sessionHash;
    private String accessTokenCipher, refreshTokenCipher, encryptionKeyVersion, email, role, csrfHash;
    private long principalId, generation;
    private Instant expiresAt, createdAt, revokedAt, refreshClaimedUntil, updatedAt;
    protected JpaWebSessionEntity() { }
    public JpaWebSessionEntity(String hash, String access, String refresh, String version, long principal,
            String email, String role, String csrf, long generation, Instant expires, Instant created, Instant updated) {
        this.sessionHash=hash; accessTokenCipher=access; refreshTokenCipher=refresh; encryptionKeyVersion=version;
        principalId=principal; this.email=email; this.role=role; csrfHash=csrf; this.generation=generation;
        expiresAt=expires; createdAt=created; updatedAt=updated;
    }
    public String getSessionHash(){return sessionHash;} public String getAccessTokenCipher(){return accessTokenCipher;}
    public String getRefreshTokenCipher(){return refreshTokenCipher;} public String getEncryptionKeyVersion(){return encryptionKeyVersion;}
    public long getPrincipalId(){return principalId;} public String getEmail(){return email;} public String getRole(){return role;}
    public String getCsrfHash(){return csrfHash;} public long getGeneration(){return generation;} public Instant getExpiresAt(){return expiresAt;}
    public Instant getCreatedAt(){return createdAt;} public Instant getRevokedAt(){return revokedAt;} public Instant getRefreshClaimedUntil(){return refreshClaimedUntil;}
    public Instant getUpdatedAt(){return updatedAt;}
    public void setAccessTokenCipher(String v){accessTokenCipher=v;} public void setRefreshTokenCipher(String v){refreshTokenCipher=v;}
    public void setEncryptionKeyVersion(String v){encryptionKeyVersion=v;} public void setGeneration(long v){generation=v;}
    public void setRevokedAt(Instant v){revokedAt=v;} public void setRefreshClaimedUntil(Instant v){refreshClaimedUntil=v;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
