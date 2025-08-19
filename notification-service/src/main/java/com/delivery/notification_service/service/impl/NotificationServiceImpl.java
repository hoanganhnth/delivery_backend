package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.common.constants.NotificationConstants;
import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.dto.response.NotificationResponse;
import com.delivery.notification_service.dto.websocket.WebSocketMessage;
import com.delivery.notification_service.entity.Notification;
import com.delivery.notification_service.mapper.NotificationMapper;
import com.delivery.notification_service.repository.NotificationRepository;
import com.delivery.notification_service.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ✅ Notification Service Implementation theo Backend Instructions
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final WebSocketService webSocketService;
    private final FirebaseService firebaseService;
    private final RedisService redisService;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                 NotificationMapper notificationMapper,
                                 WebSocketService webSocketService,
                                 FirebaseService firebaseService,
                                 RedisService redisService) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.webSocketService = webSocketService;
        this.firebaseService = firebaseService;
        this.redisService = redisService;
    }

    @Override
    @Transactional
    public NotificationResponse sendNotification(SendNotificationRequest request) {
        // Create notification in database
        NotificationResponse notification = createNotification(request);
        
        // Send via WebSocket if user is online
        if (request.getSendWebSocket()) {
            sendWebSocketNotification(notification);
        }
        
        // Send push notification
        if (request.getSendPush()) {
            sendPushNotification(notification);
        }
        
        // Update status to SENT
        updateNotificationStatus(notification.getId(), NotificationConstants.STATUS_SENT);
        
        log.info("📤 Successfully sent notification {} to user {}", notification.getId(), notification.getUserId());
        return notification;
    }

    @Override
    @Transactional
    public NotificationResponse createNotification(SendNotificationRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.getUserId());
        notification.setTitle(request.getTitle());
        notification.setMessage(request.getMessage());
        notification.setType(request.getType());
        notification.setPriority(request.getPriority());
        notification.setRelatedEntityId(request.getRelatedEntityId());
        notification.setRelatedEntityType(request.getRelatedEntityType());
        notification.setData(request.getData());
        notification.setStatus(NotificationConstants.STATUS_PENDING);
        
        Notification saved = notificationRepository.save(notification);
        
        // Cache for quick access
        NotificationResponse response = notificationMapper.toResponse(saved);
        redisService.cacheNotification(saved.getId(), response);
        
        log.info("✅ Created notification {} for user {}", saved.getId(), saved.getUserId());
        return response;
    }

    @Override
    public List<NotificationResponse> getUserNotifications(Long userId) {
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return notificationMapper.toResponseList(notifications);
    }

    @Override
    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        List<Notification> notifications = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false);
        return notificationMapper.toResponseList(notifications);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        LocalDateTime readAt = LocalDateTime.now();
        int updated = notificationRepository.markAsRead(notificationId, readAt);
        
        if (updated > 0) {
            // Remove from cache to force refresh
            redisService.removeCachedNotification(notificationId);
            
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new RuntimeException("Notification not found"));
            
            log.info("👁️ Marked notification {} as read", notificationId);
            return notificationMapper.toResponse(notification);
        }
        
        throw new RuntimeException("Failed to mark notification as read");
    }

    @Override
    @Transactional
    public int markAllAsRead(Long userId) {
        LocalDateTime readAt = LocalDateTime.now();
        int updated = notificationRepository.markAllAsReadByUser(userId, readAt);
        
        log.info("👁️ Marked {} notifications as read for user {}", updated, userId);
        return updated;
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsRead(userId, false);
    }

    @Override
    public NotificationResponse getNotificationById(Long id) {
        // Try cache first
        Object cached = redisService.getCachedNotification(id);
        if (cached instanceof NotificationResponse) {
            return (NotificationResponse) cached;
        }
        
        // Fallback to database
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        NotificationResponse response = notificationMapper.toResponse(notification);
        redisService.cacheNotification(id, response);
        
        return response;
    }

    @Override
    @Transactional
    public void deleteNotification(Long id) {
        notificationRepository.deleteById(id);
        redisService.removeCachedNotification(id);
        log.info("🗑️ Deleted notification {}", id);
    }

    @Override
    public void sendOrderCreatedNotification(Long userId, Long orderId, String restaurantName) {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(userId);
        request.setTitle("Đơn hàng đã được tạo");
        request.setMessage("Đơn hàng #" + orderId + " từ " + restaurantName + " đã được tạo thành công");
        request.setType(NotificationConstants.ORDER_CREATED);
        request.setPriority(NotificationConstants.PRIORITY_MEDIUM);
        request.setRelatedEntityId(orderId);
        request.setRelatedEntityType("ORDER");
        
        sendNotification(request);
    }

    @Override
    public void sendOrderStatusNotification(Long userId, Long orderId, String status, String restaurantName) {
        String title = getOrderStatusTitle(status);
        String message = getOrderStatusMessage(orderId, status, restaurantName);
        
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(userId);
        request.setTitle(title);
        request.setMessage(message);
        request.setType(getOrderStatusType(status));
        request.setPriority(getOrderStatusPriority(status));
        request.setRelatedEntityId(orderId);
        request.setRelatedEntityType("ORDER");
        
        sendNotification(request);
    }

    @Override
    public void sendDeliveryStatusNotification(Long userId, Long deliveryId, String status, String shipperName) {
        String title = getDeliveryStatusTitle(status);
        String message = getDeliveryStatusMessage(deliveryId, status, shipperName);
        
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(userId);
        request.setTitle(title);
        request.setMessage(message);
        request.setType(getDeliveryStatusType(status));
        request.setPriority(NotificationConstants.PRIORITY_HIGH);
        request.setRelatedEntityId(deliveryId);
        request.setRelatedEntityType("DELIVERY");
        
        sendNotification(request);
    }

    @Override
    public void sendShipperAssignedNotification(Long userId, Long deliveryId, String shipperName, String shipperPhone) {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(userId);
        request.setTitle("Shipper đã được phân công");
        request.setMessage(shipperName + " (" + shipperPhone + ") sẽ giao đơn hàng của bạn");
        request.setType(NotificationConstants.SHIPPER_ASSIGNED);
        request.setPriority(NotificationConstants.PRIORITY_HIGH);
        request.setRelatedEntityId(deliveryId);
        request.setRelatedEntityType("DELIVERY");
        
        sendNotification(request);
    }

    @Override
    public void sendSystemNotification(Long userId, String title, String message, String type) {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(userId);
        request.setTitle(title);
        request.setMessage(message);
        request.setType(type);
        request.setPriority(NotificationConstants.PRIORITY_MEDIUM);
        
        sendNotification(request);
    }

    @Override
    public void sendBroadcastNotification(String title, String message, String type) {
        // This would typically get all user IDs and send to each
        // For now, log the broadcast intention
        log.info("📢 Broadcast notification: {} - {}", title, message);
        
        // Could also use Firebase topic messaging here
        Map<String, String> data = new HashMap<>();
        data.put("type", type);
        firebaseService.sendToTopic("all_users", title, message, data);
    }

    @Override
    public void sendShipperMatchFoundNotification(Long shipperId, Long orderId, String restaurantName,
                                                String pickupAddress, String deliveryAddress,
                                                Double distance, Double estimatedPrice, Integer estimatedTime) {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(shipperId);
        request.setTitle("🎯 Đơn hàng phù hợp!");
        request.setMessage(String.format("Đơn hàng #%d từ %s - Khoảng cách: %.1fkm - Phí: %,.0f VND - Thời gian: %d phút",
                orderId, restaurantName, distance, estimatedPrice, estimatedTime));
        request.setType(NotificationConstants.MATCH_FOUND);
        request.setPriority(NotificationConstants.PRIORITY_HIGH);
        request.setRelatedEntityId(orderId);
        request.setRelatedEntityType("ORDER");
        request.setSendPush(false); // Only send WebSocket for match found  
        request.setSendWebSocket(true);
        
        // Add detailed info to data field
        Map<String, Object> data = new HashMap<>();
        data.put("pickupAddress", pickupAddress);
        data.put("deliveryAddress", deliveryAddress);
        data.put("distance", distance);
        data.put("estimatedPrice", estimatedPrice);
        data.put("estimatedTime", estimatedTime);
        request.setData(data.toString());
        
        sendNotification(request);
        log.info("🎯 Sent match found notification to shipper {}", shipperId);
    }

    @Override
    public void sendShipperDeliveryRequestNotification(Long shipperId, Long orderId, String restaurantName,
                                                     String customerName, String pickupAddress, String deliveryAddress,
                                                     Double orderValue, Double estimatedPrice, Integer estimatedTime) {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(shipperId);
        request.setTitle("📦 Yêu cầu giao hàng");
        request.setMessage(String.format("Đơn hàng #%d cho %s từ %s - Giá trị: %,.0f VND - Phí: %,.0f VND",
                orderId, customerName, restaurantName, orderValue, estimatedPrice));
        request.setType(NotificationConstants.DELIVERY_REQUEST);
        request.setPriority(NotificationConstants.PRIORITY_HIGH);
        request.setRelatedEntityId(orderId);
        request.setRelatedEntityType("ORDER");
        
        Map<String, Object> data = new HashMap<>();
        data.put("customerName", customerName);
        data.put("pickupAddress", pickupAddress);
        data.put("deliveryAddress", deliveryAddress);
        data.put("orderValue", orderValue);
        data.put("estimatedPrice", estimatedPrice);
        data.put("estimatedTime", estimatedTime);
        request.setData(data.toString());
        
        sendNotification(request);
        log.info("📦 Sent delivery request notification to shipper {}", shipperId);
    }

    @Override
    public void sendCustomerShipperAcceptedNotification(Long userId, Long orderId, String shipperName,
                                                      String shipperPhone, Integer estimatedTime) {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(userId);
        request.setTitle("✅ Shipper đã nhận đơn hàng!");
        request.setMessage(String.format("%s (%s) đã nhận đơn hàng #%d của bạn. Thời gian dự kiến: %d phút",
                shipperName, shipperPhone, orderId, estimatedTime));
        request.setType(NotificationConstants.SHIPPER_ACCEPTED);
        request.setPriority(NotificationConstants.PRIORITY_HIGH);
        request.setRelatedEntityId(orderId);
        request.setRelatedEntityType("ORDER");
        
        Map<String, Object> data = new HashMap<>();
        data.put("shipperName", shipperName);
        data.put("shipperPhone", shipperPhone);
        data.put("estimatedTime", estimatedTime);
        request.setData(data.toString());
        
        sendNotification(request);
        log.info("✅ Sent shipper accepted notification to customer {}", userId);
    }

    @Override
    public void sendShipperConfirmationNotification(Long shipperId, Long orderId, String restaurantName,
                                                  String pickupAddress, String customerPhone) {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(shipperId);
        request.setTitle("✅ Xác nhận nhận đơn");
        request.setMessage(String.format("Bạn đã nhận đơn hàng #%d từ %s. Địa chỉ lấy hàng: %s. Khách hàng: %s",
                orderId, restaurantName, pickupAddress, customerPhone));
        request.setType(NotificationConstants.SHIPPER_CONFIRMED);
        request.setPriority(NotificationConstants.PRIORITY_MEDIUM);
        request.setRelatedEntityId(orderId);
        request.setRelatedEntityType("ORDER");
        
        Map<String, Object> data = new HashMap<>();
        data.put("restaurantName", restaurantName);
        data.put("pickupAddress", pickupAddress);
        data.put("customerPhone", customerPhone);
        request.setData(data.toString());
        
        sendNotification(request);
        log.info("✅ Sent confirmation notification to shipper {}", shipperId);
    }

    @Override
    public void sendShipperRejectionConfirmationNotification(Long shipperId, Long orderId, String reason) {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(shipperId);
        request.setTitle("❌ Từ chối đơn hàng");
        request.setMessage(String.format("Bạn đã từ chối đơn hàng #%d. Lý do: %s", orderId, reason));
        request.setType(NotificationConstants.SHIPPER_REJECTED);
        request.setPriority(NotificationConstants.PRIORITY_LOW);
        request.setRelatedEntityId(orderId);
        request.setRelatedEntityType("ORDER");
        
        Map<String, Object> data = new HashMap<>();
        data.put("reason", reason);
        request.setData(data.toString());
        
        sendNotification(request);
        log.info("❌ Sent rejection confirmation to shipper {}", shipperId);
    }

    // Helper methods
    private void sendWebSocketNotification(NotificationResponse notification) {
        WebSocketMessage wsMessage = new WebSocketMessage();
        wsMessage.setType("NOTIFICATION");
        wsMessage.setUserId(notification.getUserId());
        wsMessage.setTitle(notification.getTitle());
        wsMessage.setMessage(notification.getMessage());
        wsMessage.setNotificationType(notification.getType());
        wsMessage.setPriority(notification.getPriority());
        wsMessage.setRelatedEntityId(notification.getRelatedEntityId());
        wsMessage.setRelatedEntityType(notification.getRelatedEntityType());
        wsMessage.setData(notification.getData());
        wsMessage.setTimestamp(LocalDateTime.now());
        
        webSocketService.sendNotificationToUser(notification.getUserId(), wsMessage);
    }

    private void sendPushNotification(NotificationResponse notification) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", notification.getId().toString());
        data.put("type", notification.getType());
        if (notification.getRelatedEntityId() != null) {
            data.put("relatedEntityId", notification.getRelatedEntityId().toString());
            data.put("relatedEntityType", notification.getRelatedEntityType());
        }
        
        firebaseService.sendPushNotificationToUser(
                notification.getUserId(),
                notification.getTitle(),
                notification.getMessage(),
                data
        );
    }

    private void updateNotificationStatus(Long notificationId, String status) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        notification.setStatus(status);
        if (NotificationConstants.STATUS_SENT.equals(status)) {
            notification.setSentAt(LocalDateTime.now());
        }
        
        notificationRepository.save(notification);
        redisService.removeCachedNotification(notificationId);
    }

    // Status mapping helper methods
    private String getOrderStatusTitle(String status) {
        return switch (status) {
            case "CONFIRMED" -> "Đơn hàng đã được xác nhận";
            case "PREPARING" -> "Đang chuẩn bị đơn hàng";
            case "READY" -> "Đơn hàng đã sẵn sàng";
            case "PICKED_UP" -> "Đơn hàng đã được lấy";
            case "DELIVERED" -> "Đơn hàng đã được giao";
            case "CANCELLED" -> "Đơn hàng đã bị hủy";
            default -> "Cập nhật đơn hàng";
        };
    }

    private String getOrderStatusMessage(Long orderId, String status, String restaurantName) {
        return switch (status) {
            case "CONFIRMED" -> "Đơn hàng #" + orderId + " từ " + restaurantName + " đã được xác nhận";
            case "PREPARING" -> restaurantName + " đang chuẩn bị đơn hàng #" + orderId + " của bạn";
            case "READY" -> "Đơn hàng #" + orderId + " đã sẵn sàng để giao";
            case "PICKED_UP" -> "Shipper đã lấy đơn hàng #" + orderId + " và đang trên đường giao đến bạn";
            case "DELIVERED" -> "Đơn hàng #" + orderId + " đã được giao thành công";
            case "CANCELLED" -> "Đơn hàng #" + orderId + " đã bị hủy";
            default -> "Đơn hàng #" + orderId + " có cập nhật mới";
        };
    }

    private String getOrderStatusType(String status) {
        return switch (status) {
            case "CONFIRMED" -> NotificationConstants.ORDER_CONFIRMED;
            case "PREPARING" -> NotificationConstants.ORDER_PREPARING;
            case "READY" -> NotificationConstants.ORDER_READY;
            case "PICKED_UP" -> NotificationConstants.ORDER_PICKED_UP;
            case "DELIVERED" -> NotificationConstants.ORDER_DELIVERED;
            case "CANCELLED" -> NotificationConstants.ORDER_CANCELLED;
            default -> "ORDER_STATUS_UPDATED";
        };
    }

    private String getOrderStatusPriority(String status) {
        return switch (status) {
            case "READY", "PICKED_UP", "DELIVERED" -> NotificationConstants.PRIORITY_HIGH;
            case "CANCELLED" -> NotificationConstants.PRIORITY_MEDIUM;
            default -> NotificationConstants.PRIORITY_LOW;
        };
    }

    private String getDeliveryStatusTitle(String status) {
        return switch (status) {
            case "STARTED" -> "Giao hàng đã bắt đầu";
            case "IN_PROGRESS" -> "Đang giao hàng";
            case "COMPLETED" -> "Giao hàng hoàn thành";
            default -> "Cập nhật giao hàng";
        };
    }

    private String getDeliveryStatusMessage(Long deliveryId, String status, String shipperName) {
        return switch (status) {
            case "STARTED" -> shipperName + " đã bắt đầu giao đơn hàng của bạn";
            case "IN_PROGRESS" -> shipperName + " đang trên đường giao hàng";
            case "COMPLETED" -> "Đơn hàng đã được " + shipperName + " giao thành công";
            default -> "Có cập nhật mới về giao hàng";
        };
    }

    private String getDeliveryStatusType(String status) {
        return switch (status) {
            case "STARTED" -> NotificationConstants.DELIVERY_STARTED;
            case "COMPLETED" -> NotificationConstants.DELIVERY_COMPLETED;
            default -> "DELIVERY_STATUS_UPDATED";
        };
    }
}
