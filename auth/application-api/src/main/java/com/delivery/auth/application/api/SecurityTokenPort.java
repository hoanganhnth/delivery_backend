package com.delivery.auth.application.api;
import com.delivery.auth.domain.model.SecurityTokenPurpose;
import java.time.LocalDateTime;
import java.util.Optional;
public interface SecurityTokenPort {
    String randomRawToken();
    Optional<Token> findForUpdate(String rawToken);
    void consumeOutstanding(Long accountId, SecurityTokenPurpose purpose, LocalDateTime at);
    void issue(Long accountId, SecurityTokenPurpose purpose, String rawToken, LocalDateTime expiresAt);
    void consume(Long tokenId, LocalDateTime at);
    void revokeCredentials(Long accountId, LocalDateTime at);
    void deleteExpiredBefore(LocalDateTime cutoff);
    record Token(Long id, Long accountId, SecurityTokenPurpose purpose,
            LocalDateTime expiresAt, LocalDateTime consumedAt, Boolean accountActive) {}
}
