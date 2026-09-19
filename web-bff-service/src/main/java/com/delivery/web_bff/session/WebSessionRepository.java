package com.delivery.web_bff.session;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface WebSessionRepository extends JpaRepository<WebSession, String> {
    @Query("select session from WebSession session where session.sessionHash = :hash and session.revokedAt is null and session.expiresAt > :now")
    Optional<WebSession> findActive(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WebSession session set session.refreshClaimedUntil = :leaseUntil, session.updatedAt = :now "
            + "where session.sessionHash = :hash and session.generation = :generation "
            + "and session.revokedAt is null and session.expiresAt > :now "
            + "and (session.refreshClaimedUntil is null or session.refreshClaimedUntil <= :now)")
    int claimRefresh(@Param("hash") String hash, @Param("generation") long generation,
            @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);
}
