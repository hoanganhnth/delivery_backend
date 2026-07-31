package com.delivery.auth_service.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.delivery.auth_service.entity.RefreshTokenRecord;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRecordRepository extends JpaRepository<RefreshTokenRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshTokenRecord token "
            + "join fetch token.authSession session join fetch session.authAccount "
            + "where token.tokenHash = :tokenHash")
    Optional<RefreshTokenRecord> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshTokenRecord> findAllByAuthSessionIdOrderByIdAsc(Long sessionId);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshTokenRecord token set token.state = :revokedState, token.revokedAt = :revokedAt "
            + "where token.authSession.id = :sessionId and token.state <> :revokedState")
    int revokeFamily(
            @Param("sessionId") Long sessionId,
            @Param("revokedState") RefreshTokenRecord.State revokedState,
            @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshTokenRecord token set token.state = :revokedState, token.revokedAt = :revokedAt "
            + "where token.authSession.authAccount.id = :accountId "
            + "and token.authSession.deviceId = :deviceId and token.state <> :revokedState")
    int revokeAccountDevice(
            @Param("accountId") Long accountId,
            @Param("deviceId") String deviceId,
            @Param("revokedState") RefreshTokenRecord.State revokedState,
            @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshTokenRecord token set token.state = :revokedState, token.revokedAt = :revokedAt "
            + "where token.authSession.authAccount.id = :accountId and token.state <> :revokedState")
    int revokeAccount(
            @Param("accountId") Long accountId,
            @Param("revokedState") RefreshTokenRecord.State revokedState,
            @Param("revokedAt") LocalDateTime revokedAt);
}
