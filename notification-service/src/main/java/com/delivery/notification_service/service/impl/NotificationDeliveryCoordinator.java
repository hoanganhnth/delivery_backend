package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.common.constants.NotificationConstants;
import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.dto.response.NotificationResponse;
import com.delivery.notification_service.entity.Notification;
import com.delivery.notification_service.repository.NotificationRepository;
import com.delivery.notification_service.service.FirebaseService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class NotificationDeliveryCoordinator {

    private final NotificationRepository notificationRepository;
    private final FirebaseService firebaseService;

    public NotificationDeliveryCoordinator(NotificationRepository notificationRepository,
            FirebaseService firebaseService) {
        this.notificationRepository = notificationRepository;
        this.firebaseService = firebaseService;
    }

    @Transactional
    public void deliverPending(SendNotificationRequest request, NotificationResponse snapshot) {
        Notification notification = notificationRepository.findByIdForUpdate(snapshot.getId())
                .orElseThrow(() -> new IllegalStateException("Notification not found: " + snapshot.getId()));
        if (NotificationConstants.STATUS_SENT.equals(notification.getStatus())) {
            return;
        }
        if (!NotificationConstants.STATUS_PENDING.equals(notification.getStatus())) {
            throw new IllegalStateException("Notification " + notification.getId()
                    + " is not deliverable from status " + notification.getStatus());
        }

        if (Boolean.TRUE.equals(request.getSendPush())) {
            firebaseService.sendPushNotificationToUser(
                    notification.getUserId(),
                    notification.getTitle(),
                    notification.getMessage(),
                    pushData(notification));
        }

        notification.setStatus(NotificationConstants.STATUS_SENT);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    private Map<String, String> pushData(Notification notification) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", notification.getId().toString());
        data.put("type", notification.getType());
        if (notification.getRelatedEntityId() != null) {
            data.put("relatedEntityId", notification.getRelatedEntityId().toString());
            data.put("relatedEntityType", notification.getRelatedEntityType());
        }
        return data;
    }
}
