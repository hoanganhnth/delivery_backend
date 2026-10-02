package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.Session;
import java.time.LocalDateTime;

public interface RefreshCredentialPort {
    void revokeDevice(Long accountId, String deviceId, LocalDateTime revokedAt);
    void rememberCurrent(Session session, String rawToken, LocalDateTime issuedAt, LocalDateTime expiresAt);
}
