package com.delivery.web_bff.infrastructure.session;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.domain.session.WebSession;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaSessionsAdapter implements Ports.Sessions {
    private final JpaWebSessionRepository repository;

    public JpaSessionsAdapter(JpaWebSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(WebSession session) {
        repository.save(toEntity(session));
    }

    @Override
    public Optional<WebSession> active(String hash, Instant now) {
        return repository.active(hash, now).map(this::toDomain);
    }

    @Override
    public Optional<WebSession> byHash(String hash) {
        return repository.findById(hash).map(this::toDomain);
    }

    @Override
    public boolean claimRefresh(String hash, long generation, Instant now, Instant lease) {
        return repository.claim(hash, generation, now, lease) == 1;
    }

    @Override
    @Transactional
    public Optional<WebSession> mutate(String hash, Consumer<WebSession> transition) {
        return repository.locked(hash).map(entity -> {
            WebSession session = toDomain(entity);
            transition.accept(session);
            repository.save(toEntity(session));
            return session;
        });
    }

    private WebSession toDomain(JpaWebSessionEntity entity) {
        return WebSession.rehydrate(entity.getSessionHash(), entity.getAccessTokenCipher(),
                entity.getRefreshTokenCipher(), entity.getEncryptionKeyVersion(), entity.getPrincipalId(),
                entity.getEmail(), entity.getRole(), entity.getCsrfHash(), entity.getGeneration(),
                entity.getExpiresAt(), entity.getCreatedAt(), entity.getRevokedAt(),
                entity.getRefreshClaimedUntil(), entity.getUpdatedAt());
    }

    private JpaWebSessionEntity toEntity(WebSession session) {
        JpaWebSessionEntity entity = new JpaWebSessionEntity(session.sessionHash(), session.accessTokenCipher(),
                session.refreshTokenCipher(), session.encryptionKeyVersion(), session.principalId(),
                session.email(), session.role(), session.csrfHash(), session.generation(),
                session.expiresAt(), session.createdAt(), session.updatedAt());
        entity.setRevokedAt(session.revokedAt());
        entity.setRefreshClaimedUntil(session.refreshClaimedUntil());
        return entity;
    }
}
