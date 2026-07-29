package com.delivery.notification_service.repository;

import com.delivery.notification_service.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ✅ Notification Repository theo Backend Instructions
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(
            Long userId, Boolean isRead, Pageable pageable);
    
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

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notification n WHERE n.id = :id")
    Optional<Notification> findByIdForUpdate(@Param("id") Long id);

    long deleteByIdAndUserId(Long id, Long userId);
}
