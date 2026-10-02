package com.delivery.auth_service.service;

import com.delivery.auth.application.api.RefreshCredentialPort;
import com.delivery.auth.domain.model.Session;
import com.delivery.auth_service.entity.RefreshTokenRecord;
import com.delivery.auth_service.repository.RefreshTokenRecordRepository;
import com.delivery.auth_service.repository.AuthSessionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaRefreshCredentialAdapter implements RefreshCredentialPort, com.delivery.auth.application.api.SessionCredentialRevocationPort {
    private final RefreshTokenRecordRepository records;
    private final AuthSessionRepository sessions;
    @Override public void revokeFamily(Long sessionId, LocalDateTime at) {
        records.revokeFamily(sessionId, RefreshTokenRecord.State.REVOKED, at);
    }
    @Override public void revokeDevice(Long accountId, String deviceId, LocalDateTime revokedAt) {
        records.revokeAccountDevice(accountId, deviceId, RefreshTokenRecord.State.REVOKED, revokedAt);
    }
    @Override public void rememberCurrent(Session session, String rawToken, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        RefreshTokenRecord record = new RefreshTokenRecord();
        record.setAuthSession(sessions.getReferenceById(session.id()));
        record.setTokenHash(TokenFingerprint.sha256(rawToken)); record.setState(RefreshTokenRecord.State.CURRENT);
        record.setIssuedAt(issuedAt); record.setExpiresAt(expiresAt); records.save(record);
    }
}
