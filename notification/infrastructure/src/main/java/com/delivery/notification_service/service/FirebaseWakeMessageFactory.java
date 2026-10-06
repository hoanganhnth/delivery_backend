package com.delivery.notification_service.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import java.util.Map;

/**
 * Builds an alert plus data wake message without embedding domain truth in the
 * platform-specific configuration.
 */
final class FirebaseWakeMessageFactory {

    Message create(String token, Notification notification, Map<String, String> data) {
        Message.Builder builder = Message.builder()
                .setNotification(notification)
                .setToken(token)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-priority", "10")
                        .putHeader("apns-push-type", "alert")
                        .setAps(Aps.builder()
                                .setCategory("DELIVERY_NOTIFICATION")
                                .setContentAvailable(true)
                                .build())
                        .build());

        if (data != null && !data.isEmpty()) {
            builder.putAllData(data);
        }
        return builder.build();
    }
}
