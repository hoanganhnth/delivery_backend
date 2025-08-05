package com.delivery.notification_service.service;

import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.dto.response.NotificationResponse;

import java.util.List;

/**
 * ✅ Notification Service Interface theo Backend Instructions
 */
public interface NotificationService {

    NotificationResponse sendNotification(SendNotificationRequest request);
    
    NotificationResponse createNotification(SendNotificationRequest request);
    
    List<NotificationResponse> getUserNotifications(Long userId);
    
    List<NotificationResponse> getUnreadNotifications(Long userId);
    
    NotificationResponse markAsRead(Long notificationId);
    
    int markAllAsRead(Long userId);
    
    long getUnreadCount(Long userId);
    
    NotificationResponse getNotificationById(Long id);
    
    void deleteNotification(Long id);
    
    // Order-related notifications
    void sendOrderCreatedNotification(Long userId, Long orderId, String restaurantName);
    
    void sendOrderStatusNotification(Long userId, Long orderId, String status, String restaurantName);
    
    // Delivery-related notifications  
    void sendDeliveryStatusNotification(Long userId, Long deliveryId, String status, String shipperName);
    
    void sendShipperAssignedNotification(Long userId, Long deliveryId, String shipperName, String shipperPhone);
    
    // System notifications
    void sendSystemNotification(Long userId, String title, String message, String type);
    
    void sendBroadcastNotification(String title, String message, String type);
    
    // Shipper matching notifications (from Match Service)
    void sendShipperMatchFoundNotification(Long shipperId, Long orderId, String restaurantName, 
                                         String pickupAddress, String deliveryAddress, 
                                         Double distance, Double estimatedPrice, Integer estimatedTime);
    
    void sendShipperDeliveryRequestNotification(Long shipperId, Long orderId, String restaurantName,
                                              String customerName, String pickupAddress, String deliveryAddress,
                                              Double orderValue, Double estimatedPrice, Integer estimatedTime);
    
    void sendCustomerShipperAcceptedNotification(Long userId, Long orderId, String shipperName, 
                                               String shipperPhone, Integer estimatedTime);
    
    void sendShipperConfirmationNotification(Long shipperId, Long orderId, String restaurantName,
                                           String pickupAddress, String customerPhone);
    
    void sendShipperRejectionConfirmationNotification(Long shipperId, Long orderId, String reason);
}
