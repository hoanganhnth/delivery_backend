package com.delivery.notification_service.service;

import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.dto.response.NotificationResponse;

import java.util.List;
import java.util.UUID;

/**
 * ✅ Notification Service Interface theo Backend Instructions
 */
public interface NotificationService {

    NotificationResponse sendNotification(SendNotificationRequest request);
    
    List<NotificationResponse> getUserNotifications(Long userId);
    List<NotificationResponse> getUserNotifications(Long principalId, Long legacyUserId);
    
    List<NotificationResponse> getUnreadNotifications(Long userId);
    List<NotificationResponse> getUnreadNotifications(Long principalId, Long legacyUserId);
    
    NotificationResponse markAsRead(Long notificationId, Long userId);
    NotificationResponse markAsRead(Long notificationId, Long principalId, Long legacyUserId);
    
    int markAllAsRead(Long userId);
    int markAllAsRead(Long principalId, Long legacyUserId);
    
    long getUnreadCount(Long userId);
    long getUnreadCount(Long principalId, Long legacyUserId);
    
    NotificationResponse getNotificationById(Long id, Long userId);
    NotificationResponse getNotificationById(Long id, Long principalId, Long legacyUserId);
    
    void deleteNotification(Long id, Long userId);
    void deleteNotification(Long id, Long principalId, Long legacyUserId);
    
    // Order-related notifications
    void sendOrderCreatedNotification(UUID eventId, Long userId, Long orderId, String restaurantName);
    void sendOrderCreatedNotification(UUID eventId, Long userId, Long userPrincipalId, Long orderId, String restaurantName);
    
    // Delivery-related notifications  
    void sendDeliveryStatusNotification(UUID eventId, Long userId, Long deliveryId, String status, String shipperName);
    void sendDeliveryStatusNotification(UUID eventId, Long userId, Long userPrincipalId, Long deliveryId, String status, String shipperName);
    
    // Shipper matching notifications (from Match Service)
    void sendShipperMatchFoundNotification(Long shipperId, Long orderId, String restaurantName, 
                                         String pickupAddress, String deliveryAddress, 
                                         Double distance, String offerEventId);
}
