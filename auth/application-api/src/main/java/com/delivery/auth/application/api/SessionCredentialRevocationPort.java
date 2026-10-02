package com.delivery.auth.application.api;

import java.time.LocalDateTime;

public interface SessionCredentialRevocationPort {
    void revokeFamily(Long sessionId, LocalDateTime at);
}
