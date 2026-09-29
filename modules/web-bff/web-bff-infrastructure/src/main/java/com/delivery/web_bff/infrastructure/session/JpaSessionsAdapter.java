package com.delivery.web_bff.infrastructure.session;
import com.delivery.web_bff.application.api.Ports; import com.delivery.web_bff.domain.session.WebSession;
import java.time.Instant; import java.util.Optional; import org.springframework.stereotype.Component;
@Component
public final class JpaSessionsAdapter implements Ports.Sessions {
 private final JpaWebSessionRepository repository; public JpaSessionsAdapter(JpaWebSessionRepository repository){this.repository=repository;}
 public void save(WebSession s){repository.save(toEntity(s));}
 public Optional<WebSession> active(String hash,Instant now){return repository.active(hash,now).map(this::toDomain);}
 public Optional<WebSession> byHash(String hash){return repository.findById(hash).map(this::toDomain);}
 public boolean claimRefresh(String hash,long generation,Instant now,Instant lease){return repository.claim(hash,generation,now,lease)==1;}
 private WebSession toDomain(JpaWebSessionEntity e){return WebSession.rehydrate(e.getSessionHash(),e.getAccessTokenCipher(),e.getRefreshTokenCipher(),e.getEncryptionKeyVersion(),e.getPrincipalId(),e.getEmail(),e.getRole(),e.getCsrfHash(),e.getGeneration(),e.getExpiresAt(),e.getCreatedAt(),e.getRevokedAt(),e.getRefreshClaimedUntil(),e.getUpdatedAt());}
 private JpaWebSessionEntity toEntity(WebSession s){JpaWebSessionEntity e=new JpaWebSessionEntity(s.sessionHash(),s.accessTokenCipher(),s.refreshTokenCipher(),s.encryptionKeyVersion(),s.principalId(),s.email(),s.role(),s.csrfHash(),s.generation(),s.expiresAt(),s.createdAt(),s.updatedAt());e.setRevokedAt(s.revokedAt());e.setRefreshClaimedUntil(s.refreshClaimedUntil());return e;}
}
