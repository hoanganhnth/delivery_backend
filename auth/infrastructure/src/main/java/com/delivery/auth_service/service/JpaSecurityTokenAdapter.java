package com.delivery.auth_service.service;

import com.delivery.auth.application.api.SecurityTokenPort;
import com.delivery.auth.domain.model.SecurityTokenPurpose;
import com.delivery.auth_service.entity.AuthSecurityToken;
import com.delivery.auth_service.entity.RefreshTokenRecord;
import com.delivery.auth_service.repository.*;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaSecurityTokenAdapter implements SecurityTokenPort {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final AuthSecurityTokenRepository tokens;
    private final AuthAccountRepository accounts;
    private final RefreshTokenRecordRepository refresh;
    private final AuthSessionRepository sessions;
    @Override public String randomRawToken() {
        byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    @Override public Optional<Token> findForUpdate(String raw) {
        return tokens.findByTokenHashForUpdate(TokenFingerprint.sha256(raw)).map(row -> new Token(
                row.getId(),row.getAuthAccount().getId(),
                SecurityTokenPurpose.valueOf(row.getPurpose().name()),row.getExpiresAt(),row.getConsumedAt(),
                row.getAuthAccount().getIsActive()));
    }
    @Override public void consumeOutstanding(Long accountId,SecurityTokenPurpose purpose,LocalDateTime at) {
        tokens.consumeOutstanding(accountId,AuthSecurityToken.Purpose.valueOf(purpose.name()),at);
    }
    @Override public void issue(Long accountId,SecurityTokenPurpose purpose,String raw,LocalDateTime expiresAt) {
        var row=new AuthSecurityToken();row.setAuthAccount(accounts.getReferenceById(accountId));
        row.setPurpose(AuthSecurityToken.Purpose.valueOf(purpose.name()));
        row.setTokenHash(TokenFingerprint.sha256(raw));row.setExpiresAt(expiresAt);
        tokens.saveAndFlush(row);
    }
    @Override public void consume(Long id,LocalDateTime at) {
        var token=tokens.findById(id).orElseThrow();token.setConsumedAt(at);tokens.save(token);
    }
    @Override public void revokeCredentials(Long id,LocalDateTime at) {
        refresh.revokeAccount(id,RefreshTokenRecord.State.REVOKED,at);
        sessions.deactivateAllActiveSessions(id,at);
    }
    @Override public void deleteExpiredBefore(LocalDateTime cutoff) {tokens.deleteExpiredBefore(cutoff);}
}
