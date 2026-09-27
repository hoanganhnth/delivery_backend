package com.delivery.auth_service.repository;

import com.delivery.auth_service.entity.AuthSecurityToken;
import com.delivery.auth_service.entity.AuthSecurityToken.Purpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthSecurityTokenRepository extends JpaRepository<AuthSecurityToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AuthSecurityToken token join fetch token.authAccount "
            + "where token.tokenHash = :tokenHash")
    Optional<AuthSecurityToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthSecurityToken token set token.consumedAt = :consumedAt "
            + "where token.authAccount.id = :accountId and token.purpose = :purpose "
            + "and token.consumedAt is null")
    int consumeOutstanding(@Param("accountId") Long accountId,
                           @Param("purpose") Purpose purpose,
                           @Param("consumedAt") LocalDateTime consumedAt);

    @Modifying
    @Query("delete from AuthSecurityToken token where token.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
