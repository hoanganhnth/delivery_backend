package com.delivery.notification_service.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ✅ Firebase Push Notification Service theo Backend Instructions
 */
@Slf4j
@Service
public class FirebaseService {

    private final FirebaseApp firebaseApp;
    private final RedisService redisService;

    public FirebaseService(Optional<FirebaseApp> firebaseApp, RedisService redisService) {
        this.firebaseApp = firebaseApp.orElse(null);
        this.redisService = redisService;
    }

    /**
     * Send push notification to specific user
     */
    public void sendPushNotificationToUser(Long userId, String title, String body, Map<String, String> data) {
        requirePositiveId(userId, "userId");
        requireNonBlank(title, "title");
        requireNonBlank(body, "body");
        if (firebaseApp == null) {
            log.warn("⚠️ Firebase not initialized, skipping push notification");
            return;
        }

        // Once Firebase is explicitly configured, Redis/provider failures must
        // propagate. NotificationService keeps the durable row PENDING and Kafka
        // retries the same deduplication key; swallowing here would mark it SENT.
        Set<Object> fcmTokens = redisService.getUserFcmTokens(userId);

        if (fcmTokens.isEmpty()) {
            log.debug("📱 No FCM tokens found for user {}", userId);
            return;
        }

        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        for (Object tokenObj : fcmTokens) {
            String token = tokenObj.toString();
            sendToToken(token, notification, data, userId);
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
                throw new IllegalStateException("Firebase push delivery failed", e);
            }
        }
    }

    /**
     * Register FCM token for user
     */
    public void registerFcmToken(Long userId, String fcmToken) {
        requirePositiveId(userId, "userId");
        requireNonBlank(fcmToken, "fcmToken");
        redisService.storeFcmToken(userId, fcmToken);
        log.info("📱 Registered FCM token for user {}", userId);
    }

    /**
     * Unregister FCM token for user
     */
    public void unregisterFcmToken(Long userId, String fcmToken) {
        requirePositiveId(userId, "userId");
        requireNonBlank(fcmToken, "fcmToken");
        redisService.removeFcmToken(userId, fcmToken);
        log.info("🗑️ Unregistered FCM token for user {}", userId);
    }

    private void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

}
