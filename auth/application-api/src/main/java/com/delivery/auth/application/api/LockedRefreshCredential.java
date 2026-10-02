package com.delivery.auth.application.api;

import java.time.LocalDateTime;

/** Mutations of the credential and session held by the persistence lock. */
public interface LockedRefreshCredential {
    RefreshCredentialFacts facts();
    void revokeFamily(LocalDateTime at);
    void markRotated(LocalDateTime at);
    void rememberSuccessor(String rawToken, LocalDateTime issuedAt, LocalDateTime expiresAt);
    void updateSessionActivity(LocalDateTime lastLoginAt, LocalDateTime expiresAt);
}
