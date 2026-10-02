package com.delivery.web_bff.infrastructure.session;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JpaWebSessionRepository extends JpaRepository<JpaWebSessionEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from JpaWebSessionEntity s where s.sessionHash=:hash")
    Optional<JpaWebSessionEntity> locked(@Param("hash") String hash);

    @Query("select s from JpaWebSessionEntity s where s.sessionHash=:hash and s.revokedAt is null and s.expiresAt > :now")
    Optional<JpaWebSessionEntity> active(@Param("hash") String hash, @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JpaWebSessionEntity s set s.refreshClaimedUntil=:lease, s.updatedAt=:now
            where s.sessionHash=:hash and s.generation=:generation and s.revokedAt is null
            and s.expiresAt > :now and (s.refreshClaimedUntil is null or s.refreshClaimedUntil <= :now)
            """)
    int claim(@Param("hash") String hash, @Param("generation") long generation,
            @Param("now") Instant now, @Param("lease") Instant lease);
}
