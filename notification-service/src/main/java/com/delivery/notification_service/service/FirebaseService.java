package com.delivery.notification_service.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final FirebaseWakeMessageFactory messageFactory;

    @Autowired
    public FirebaseService(Optional<FirebaseApp> firebaseApp, RedisService redisService) {
        this(firebaseApp, redisService, new FirebaseWakeMessageFactory());
    }

    FirebaseService(
            Optional<FirebaseApp> firebaseApp,
            RedisService redisService,
            FirebaseWakeMessageFactory messageFactory) {
        this.firebaseApp = firebaseApp.orElse(null);
        this.redisService = redisService;
        this.messageFactory = messageFactory;
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
            Message message = messageFactory.create(token, notification, data);

            // Send message
            FirebaseMessaging.getInstance(firebaseApp).send(message);
            log.info("📱 Successfully sent push notification to user {}", userId);

        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                // Remove invalid token
                redisService.removeFcmToken(userId, token);
                log.warn("🗑️ Removed invalid FCM token for user {}", userId);
            } else {
                log.error(
                        "💥 Firebase push delivery failed for user {} with code {}",
                        userId,
                        e.getMessagingErrorCode());
                // Do not attach the provider exception: Kafka/HTTP boundaries may
                // log the propagated stack, and provider messages can contain
                // request metadata. The stable message still triggers retry.
                throw new IllegalStateException("Firebase push delivery failed");
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
