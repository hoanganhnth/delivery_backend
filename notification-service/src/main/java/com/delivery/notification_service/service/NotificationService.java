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
    
    List<NotificationResponse> getUnreadNotifications(Long userId);
    
    NotificationResponse markAsRead(Long notificationId, Long userId);
    
    int markAllAsRead(Long userId);
    
    long getUnreadCount(Long userId);
    
    NotificationResponse getNotificationById(Long id, Long userId);
    
    void deleteNotification(Long id, Long userId);
    
    // Order-related notifications
    void sendOrderCreatedNotification(UUID eventId, Long userId, Long orderId, String restaurantName);
    
    // Delivery-related notifications  
    void sendDeliveryStatusNotification(UUID eventId, Long userId, Long deliveryId, String status, String shipperName);
    
    // Shipper matching notifications (from Match Service)
    void sendShipperMatchFoundNotification(Long shipperId, Long orderId, String restaurantName, 
                                         String pickupAddress, String deliveryAddress, 
                                         Double distance, String offerEventId);
}
