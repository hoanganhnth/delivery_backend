package com.delivery.auth_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

import jakarta.persistence.LockModeType;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session join fetch session.authAccount "
            + "where session.authAccount.id = :accountId and session.deviceId = :deviceId "
            + "order by session.id")
    List<AuthSession> findAccountDeviceSessionsForUpdate(
            @Param("accountId") Long accountId,
            @Param("deviceId") String deviceId);

    @Query("select session from AuthSession session where session.authAccount = :account "
            + "and session.isActive = true and session.expiresAt > :now "
            + "order by session.lastLoginAt desc")
    List<AuthSession> findActiveUnexpiredByAuthAccount(
            @Param("account") AuthAccount account,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("update AuthSession session set session.isActive = false, session.expiresAt = :now "
            + "where session.authAccount.id = :accountId and session.isActive = true")
    int deactivateAllActiveSessions(
            @Param("accountId") Long accountId,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("update AuthSession session set session.isActive = false, session.expiresAt = :now "
            + "where session.authAccount.id = :accountId and session.deviceId = :deviceId "
            + "and session.isActive = true")
    int deactivateActiveSessionsForDevice(
            @Param("accountId") Long accountId,
            @Param("deviceId") String deviceId,
            @Param("now") LocalDateTime now);
}
