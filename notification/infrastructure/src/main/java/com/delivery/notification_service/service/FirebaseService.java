package com.delivery.notification_service.service;

import com.google.firebase.FirebaseApp;
import com.delivery.notification.application.DispatchPush;
import com.delivery.notification.application.api.PushPort;
import com.delivery.notification.domain.PushEligibility;
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
        new DispatchPush(new PushPort() {
            public boolean configured() {
                if (firebaseApp == null) log.warn("⚠️ Firebase not initialized, skipping push notification");
                return firebaseApp != null;
            }
            public Set<Object> tokens(Long id) {
                Set<Object> tokens = redisService.getUserFcmTokens(id);
                if (tokens.isEmpty()) log.debug("📱 No FCM tokens found for user {}", id);
                return tokens;
            }
            public Outcome send(String token, String alertTitle, String alertBody, Map<String, String> payload, Long id) {
                Notification alert = Notification.builder().setTitle(alertTitle).setBody(alertBody).build();
                return sendToToken(token, alert, payload, id);
            }
            public void removeToken(Long id, String token) {
                redisService.removeFcmToken(id, token);
                log.warn("🗑️ Removed invalid FCM token for user {}", id);
            }
        }).send(userId, title, body, data);
    }

    /**
     * Send to specific FCM token
     */
    private PushPort.Outcome sendToToken(String token, Notification notification, Map<String, String> data, Long userId) {
        try {
            Message message = messageFactory.create(token, notification, data);

            // Send message
            FirebaseMessaging.getInstance(firebaseApp).send(message);
            log.info("📱 Successfully sent push notification to user {}", userId);
            return PushPort.Outcome.SENT;

        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                // Remove invalid token
                return PushPort.Outcome.UNREGISTERED;
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
        PushEligibility.validateToken(userId, fcmToken);
        redisService.storeFcmToken(userId, fcmToken);
        log.info("📱 Registered FCM token for user {}", userId);
    }

    /**
     * Unregister FCM token for user
     */
    public void unregisterFcmToken(Long userId, String fcmToken) {
        PushEligibility.validateToken(userId, fcmToken);
        redisService.removeFcmToken(userId, fcmToken);
        log.info("🗑️ Unregistered FCM token for user {}", userId);
    }

}
