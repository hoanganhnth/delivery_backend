package com.delivery.auth_service.service;

import com.delivery.auth.application.api.*;
import com.delivery.auth_service.entity.AuthSession;
import com.delivery.auth_service.entity.RefreshTokenRecord;
import com.delivery.auth_service.repository.AuthSessionRepository;
import com.delivery.auth_service.repository.RefreshTokenRecordRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaRefreshCredentialLockAdapter implements RefreshCredentialLockPort {
    private final RefreshTokenRecordRepository records;
    private final AuthSessionRepository sessions;

    @Override public <T> T withLocked(String rawToken, Function<Optional<LockedRefreshCredential>, T> operation) {
        return operation.apply(records.findByTokenHashForUpdate(TokenFingerprint.sha256(rawToken))
                .map(LockedCredential::new));
    }

    private final class LockedCredential implements LockedRefreshCredential {
        private final RefreshTokenRecord record;
        private final AuthSession session;
        private LockedCredential(RefreshTokenRecord record) {
            this.record = record;
            this.session = record.getAuthSession();
        }
        @Override public RefreshCredentialFacts facts() {
            return new RefreshCredentialFacts(record.getState() == null ? null
                    : com.delivery.auth.domain.model.RefreshCredentialState.valueOf(record.getState().name()),
                    JpaAuthSessionAdapter.toDomain(session), AuthAccountMapping.toDomain(session.getAuthAccount()));
        }
        @Override public void revokeFamily(LocalDateTime at) {
            records.revokeFamily(session.getId(), RefreshTokenRecord.State.REVOKED, at);
            session.setIsActive(false); session.setExpiresAt(at); sessions.save(session);
        }
        @Override public void markRotated(LocalDateTime at) {
            record.setState(RefreshTokenRecord.State.ROTATED); record.setRotatedAt(at); records.save(record);
        }
        @Override public void rememberSuccessor(String rawToken, LocalDateTime issuedAt, LocalDateTime expiresAt) {
            RefreshTokenRecord successor = new RefreshTokenRecord();
            successor.setAuthSession(session); successor.setTokenHash(TokenFingerprint.sha256(rawToken));
            successor.setState(RefreshTokenRecord.State.CURRENT);
            successor.setIssuedAt(issuedAt); successor.setExpiresAt(expiresAt); records.save(successor);
        }
        @Override public void updateSessionActivity(LocalDateTime lastLoginAt, LocalDateTime expiresAt) {
            session.setLastLoginAt(lastLoginAt); session.setExpiresAt(expiresAt); sessions.save(session);
        }
    }
}
