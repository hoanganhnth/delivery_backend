package com.delivery.auth_service.repository;

import com.delivery.auth_service.entity.AuthAccount;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

@Repository
public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {
    Optional<AuthAccount> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from AuthAccount account where account.id = :id")
    Optional<AuthAccount> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select account from AuthAccount account
            where account.userStatusSyncPending = true
              and account.userId is not null
            order by account.userStatusSyncUpdatedAt asc, account.id asc
            """)
    List<AuthAccount> findPendingUserStatusSync(Pageable pageable);

    @Transactional
    @Modifying
    @Query("""
            update AuthAccount account
            set account.userStatusSyncPending = false,
                account.userStatusSyncAdminId = null,
                account.userStatusSyncBlockReason = null,
                account.userStatusSyncAttempts = 0,
                account.userStatusSyncLastError = null,
                account.userStatusSyncUpdatedAt = :syncedAt
            where account.id = :accountId
              and account.userStatusSyncVersion = :version
            """)
    int clearUserStatusSyncPending(
            @Param("accountId") Long accountId,
            @Param("version") Long version,
            @Param("syncedAt") LocalDateTime syncedAt);

    @Transactional
    @Modifying
    @Query("""
            update AuthAccount account
            set account.userStatusSyncAttempts = account.userStatusSyncAttempts + 1,
                account.userStatusSyncLastError = :error,
                account.userStatusSyncUpdatedAt = :failedAt
            where account.id = :accountId
              and account.userStatusSyncVersion = :version
            """)
    int recordUserStatusSyncFailure(
            @Param("accountId") Long accountId,
            @Param("version") Long version,
            @Param("error") String error,
            @Param("failedAt") LocalDateTime failedAt);
}
