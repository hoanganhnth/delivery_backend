package com.delivery.notification_service.repository;

import com.delivery.notification_service.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ✅ Notification Repository theo Backend Instructions
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    List<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, Boolean isRead);
    
    List<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type);
    
    List<Notification> findByStatusOrderByCreatedAtAsc(String status);
    
    List<Notification> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.createdAt >= :fromDate ORDER BY n.createdAt DESC")
    List<Notification> findRecentNotificationsByUser(@Param("userId") Long userId, @Param("fromDate") LocalDateTime fromDate);
    
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.id = :id")
    int markAsRead(@Param("id") Long id, @Param("readAt") LocalDateTime readAt);
    
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsReadByUser(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
    
    long countByUserIdAndIsRead(Long userId, Boolean isRead);
    
    List<Notification> findByRelatedEntityIdAndRelatedEntityType(Long relatedEntityId, String relatedEntityType);
}
