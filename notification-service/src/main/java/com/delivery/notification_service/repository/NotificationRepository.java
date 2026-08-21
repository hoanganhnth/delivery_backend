package com.delivery.notification_service.repository;

import com.delivery.notification_service.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ✅ Notification Repository theo Backend Instructions
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Notification> findByUserPrincipalIdOrderByCreatedAtDesc(Long principalId, Pageable pageable);

    @Query("select n from Notification n where n.userPrincipalId = :principalId "
            + "or (n.userPrincipalId is null and n.userId = :legacyUserId) order by n.createdAt desc")
    List<Notification> findByPrincipalOrUnmigratedLegacyUser(
            @Param("principalId") Long principalId, @Param("legacyUserId") Long legacyUserId, Pageable pageable);

    List<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(
            Long userId, Boolean isRead, Pageable pageable);

    List<Notification> findByUserPrincipalIdAndIsReadOrderByCreatedAtDesc(
            Long principalId, Boolean isRead, Pageable pageable);

    long countByUserPrincipalIdAndIsRead(Long principalId, Boolean isRead);

    Optional<Notification> findByIdAndUserPrincipalId(Long id, Long principalId);

    @Query("select n from Notification n where (n.userPrincipalId = :principalId "
            + "or (n.userPrincipalId is null and n.userId = :legacyUserId)) and n.isRead = :isRead order by n.createdAt desc")
    List<Notification> findUnreadByPrincipalOrUnmigratedLegacyUser(
            @Param("principalId") Long principalId, @Param("legacyUserId") Long legacyUserId,
            @Param("isRead") Boolean isRead, Pageable pageable);

    @Query("select count(n) from Notification n where (n.userPrincipalId = :principalId "
            + "or (n.userPrincipalId is null and n.userId = :legacyUserId)) and n.isRead = :isRead")
    long countByPrincipalOrUnmigratedLegacyUserAndIsRead(
            @Param("principalId") Long principalId, @Param("legacyUserId") Long legacyUserId,
            @Param("isRead") Boolean isRead);

    @Query("select n from Notification n where n.id = :id and (n.userPrincipalId = :principalId "
            + "or (n.userPrincipalId is null and n.userId = :legacyUserId))")
    Optional<Notification> findByIdAndPrincipalOrUnmigratedLegacyUser(
            @Param("id") Long id, @Param("principalId") Long principalId, @Param("legacyUserId") Long legacyUserId);
    
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt "
            + "WHERE n.id = :id AND n.userId = :userId AND n.isRead = false")
    int markAsRead(@Param("id") Long id, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
    
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsReadByUser(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
    
    long countByUserIdAndIsRead(Long userId, Boolean isRead);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    Optional<Notification> findByDeduplicationKey(String deduplicationKey);

    /**
     * Persists the durable PENDING notification before external delivery. A
     * concurrent Kafka consumer waits for the unique-key claimant and then
     * receives zero, allowing it to reuse the same stable row instead of
     * surfacing a duplicate-key retry.
     */
    @Modifying(flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = """
            INSERT INTO notifications (
                user_id, user_principal_id, title, message, type, priority, status, is_read,
                related_entity_id, related_entity_type, data,
                deduplication_key, created_at, updated_at
            ) VALUES (
                :userId, :userPrincipalId, :title, :message, :type, :priority, :status, :isRead,
                :relatedEntityId, :relatedEntityType, :data,
                :deduplicationKey, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            ) ON CONFLICT (deduplication_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsentPostgres(
            @Param("userId") Long userId,
            @Param("userPrincipalId") Long userPrincipalId,
            @Param("title") String title,
            @Param("message") String message,
            @Param("type") String type,
            @Param("priority") String priority,
            @Param("status") String status,
            @Param("isRead") Boolean isRead,
            @Param("relatedEntityId") Long relatedEntityId,
            @Param("relatedEntityType") String relatedEntityType,
            @Param("data") String data,
            @Param("deduplicationKey") String deduplicationKey);

    /** H2 fallback for local focused tests; PostgreSQL is the production race authority. */
    @Modifying(flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = """
            INSERT INTO notifications (
                user_id, user_principal_id, title, message, type, priority, status, is_read,
                related_entity_id, related_entity_type, data,
                deduplication_key, created_at, updated_at
            ) SELECT
                :userId, :userPrincipalId, :title, :message, :type, :priority, :status, :isRead,
                :relatedEntityId, :relatedEntityType, :data,
                :deduplicationKey, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            WHERE NOT EXISTS (
                SELECT 1 FROM notifications WHERE deduplication_key = :deduplicationKey
            )
            """, nativeQuery = true)
    int insertIfAbsentH2(
            @Param("userId") Long userId,
            @Param("userPrincipalId") Long userPrincipalId,
            @Param("title") String title,
            @Param("message") String message,
            @Param("type") String type,
            @Param("priority") String priority,
            @Param("status") String status,
            @Param("isRead") Boolean isRead,
            @Param("relatedEntityId") Long relatedEntityId,
            @Param("relatedEntityType") String relatedEntityType,
            @Param("data") String data,
            @Param("deduplicationKey") String deduplicationKey);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notification n WHERE n.id = :id")
    Optional<Notification> findByIdForUpdate(@Param("id") Long id);

    long deleteByIdAndUserId(Long id, Long userId);
}
