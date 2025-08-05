package com.delivery.notification_service.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ✅ Firebase Push Notification Service theo Backend Instructions
 */
@Slf4j
@Service
public class FirebaseService {

    private final FirebaseApp firebaseApp;
    private final RedisService redisService;

    public FirebaseService(FirebaseApp firebaseApp, RedisService redisService) {
        this.firebaseApp = firebaseApp;
        this.redisService = redisService;
    }

    /**
     * Send push notification to specific user
     */
    public void sendPushNotificationToUser(Long userId, String title, String body, Map<String, String> data) {
        if (firebaseApp == null) {
            log.warn("⚠️ Firebase not initialized, skipping push notification");
            return;
        }

        try {
            // Get user's FCM tokens
            Set<Object> fcmTokens = redisService.getUserFcmTokens(userId);
            
            if (fcmTokens.isEmpty()) {
                log.debug("📱 No FCM tokens found for user {}", userId);
                return;
            }

            // Build notification
            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            // Send to each token
            for (Object tokenObj : fcmTokens) {
                String token = tokenObj.toString();
                sendToToken(token, notification, data, userId);
            }

        } catch (Exception e) {
            log.error("💥 Failed to send push notification to user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Send push notification to multiple users
     */
    public void sendPushNotificationToUsers(List<Long> userIds, String title, String body, Map<String, String> data) {
        for (Long userId : userIds) {
            sendPushNotificationToUser(userId, title, body, data);
        }
    }

    /**
     * Send to specific FCM token
     */
    private void sendToToken(String token, Notification notification, Map<String, String> data, Long userId) {
        try {
            // Build message
            Message.Builder messageBuilder = Message.builder()
                    .setNotification(notification)
                    .setToken(token);

            // Add data if provided
            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            // Add Android and iOS specific configurations
            messageBuilder.setAndroidConfig(AndroidConfig.builder()
                    .setNotification(AndroidNotification.builder()
                            .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                            .build())
                    .build());

            messageBuilder.setApnsConfig(ApnsConfig.builder()
                    .setAps(Aps.builder()
                            .setCategory("DELIVERY_NOTIFICATION")
                            .build())
                    .build());

            Message message = messageBuilder.build();

            // Send message
            String response = FirebaseMessaging.getInstance(firebaseApp).send(message);
            log.info("📱 Successfully sent push notification to user {}: {}", userId, response);

        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                // Remove invalid token
                redisService.removeFcmToken(userId, token);
                log.warn("🗑️ Removed invalid FCM token for user {}", userId);
            } else {
                log.error("💥 Failed to send push notification: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Register FCM token for user
     */
    public void registerFcmToken(Long userId, String fcmToken) {
        redisService.storeFcmToken(userId, fcmToken);
        log.info("📱 Registered FCM token for user {}", userId);
    }

    /**
     * Unregister FCM token for user
     */
    public void unregisterFcmToken(Long userId, String fcmToken) {
        redisService.removeFcmToken(userId, fcmToken);
        log.info("🗑️ Unregistered FCM token for user {}", userId);
    }

    /**
     * Send topic-based notification (for broadcast messages)
     */
    public void sendToTopic(String topic, String title, String body, Map<String, String> data) {
        if (firebaseApp == null) {
            log.warn("⚠️ Firebase not initialized, skipping topic notification");
            return;
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setTopic(topic);

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            Message message = messageBuilder.build();
            String response = FirebaseMessaging.getInstance(firebaseApp).send(message);
            
            log.info("📢 Successfully sent topic notification to {}: {}", topic, response);

        } catch (FirebaseMessagingException e) {
            log.error("💥 Failed to send topic notification: {}", e.getMessage(), e);
        }
    }
}
