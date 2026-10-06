package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.common.constants.NotificationConstants;
import com.delivery.notification.application.CompleteDelivery;
import com.delivery.notification.application.api.DeliveryPort;
import com.delivery.notification.application.api.StoredNotification;
import java.util.Optional;
import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.dto.response.NotificationResponse;
import com.delivery.notification_service.entity.Notification;
import com.delivery.notification_service.repository.NotificationRepository;
import com.delivery.notification_service.service.FirebaseService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        new CompleteDelivery(
                new DeliveryPort() {
                    private Notification locked;

                    public Optional<StoredNotification<Void>> lock(Long id) {
                        return notificationRepository.findByIdForUpdate(id).map(n -> {
                            locked = n;
                            return new StoredNotification<Void>(
                                    n.getId(), NotificationServiceImpl.payload(n), n.getStatus(), null);
                        });
                    }

                    public void push(Long userId, String title, String message, Map<String, String> data) {
                        firebaseService.sendPushNotificationToUser(userId, title, message, data);
                    }

                    public void saveSent(Long id, LocalDateTime sentAt) {
                        locked.setStatus(NotificationConstants.STATUS_SENT);
                        locked.setSentAt(sentAt);
                        notificationRepository.save(locked);
                    }
                }, LocalDateTime::now).deliver(snapshot.getId(), request.getSendPush());
    }
}
