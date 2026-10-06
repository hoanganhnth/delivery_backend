package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.common.constants.NotificationConstants;
import com.delivery.notification.domain.NotificationIntent;
import com.delivery.notification.application.EventNotifications;
import com.delivery.notification.domain.ReplayPayload;
import com.delivery.notification.application.DurableSend;
import com.delivery.notification.application.Inbox;
import com.delivery.notification.domain.InboxActor;
import com.delivery.notification.application.ReplayConflictException;
import com.delivery.notification.application.api.*;
import java.util.Optional;
import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.dto.response.NotificationResponse;
import com.delivery.notification_service.entity.Notification;
import com.delivery.notification_service.exception.NotificationConflictException;
import com.delivery.notification_service.mapper.NotificationMapper;
import com.delivery.notification_service.repository.NotificationRepository;
import com.delivery.notification_service.service.*;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ✅ Notification Service Implementation theo Backend Instructions
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationDeliveryCoordinator deliveryCoordinator;
    private final MeterRegistry meterRegistry;

    @Value("${app.identity.principal-ownership.enforced:false}")
    private boolean principalOwnershipEnforced;

    @Value("${spring.datasource.url:}")
    private String dataSourceUrl;

    @Autowired
    public NotificationServiceImpl(NotificationRepository notificationRepository,
            NotificationMapper notificationMapper,
            NotificationDeliveryCoordinator deliveryCoordinator,
            MeterRegistry meterRegistry) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.deliveryCoordinator = deliveryCoordinator;
        this.meterRegistry = meterRegistry;
    }

    /** Compatibility constructor for existing focused fixtures. */
    NotificationServiceImpl(NotificationRepository notificationRepository,
            NotificationMapper notificationMapper,
            NotificationDeliveryCoordinator deliveryCoordinator) {
        this(notificationRepository, notificationMapper, deliveryCoordinator, new SimpleMeterRegistry());
    }

    NotificationServiceImpl(NotificationRepository notificationRepository,
            NotificationMapper notificationMapper,
            FirebaseService firebaseService) {
        this(notificationRepository, notificationMapper,
                new NotificationDeliveryCoordinator(notificationRepository, firebaseService), new SimpleMeterRegistry());
    }

    @Override
    public NotificationResponse sendNotification(SendNotificationRequest request) {
        SendCommand command = request == null ? null : new SendCommand(payload(request),
                request.getDeduplicationKey(), request.getSendPush());
        try {
            return new DurableSend<>(new DurableSendPort<NotificationResponse>() {
                public Optional<StoredNotification<NotificationResponse>> findByKey(String key) {
                    return notificationRepository.findByDeduplicationKey(key).map(NotificationServiceImpl.this::stored);
                }
                public StoredNotification<NotificationResponse> createCommitted(SendCommand ignored) {
                    return createNotification(request);
                }
                public void deliver(SendCommand ignored, StoredNotification<NotificationResponse> notification) {
                    deliveryCoordinator.deliverPending(request, notification.response());
                }
                public void markResponseSent(NotificationResponse response) {
                    response.setStatus(NotificationConstants.STATUS_SENT);
                }
            }).send(command);
        } catch (ReplayConflictException conflict) {
            throw new NotificationConflictException(conflict.getMessage());
        }
    }

    private StoredNotification<NotificationResponse> createNotification(SendNotificationRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.getUserId());
        notification.setUserPrincipalId(request.getUserPrincipalId());
        notification.setTitle(request.getTitle());
        notification.setMessage(request.getMessage());
        notification.setType(request.getType());
        notification.setPriority(request.getPriority());
        notification.setRelatedEntityId(request.getRelatedEntityId());
        notification.setRelatedEntityType(request.getRelatedEntityType());
        notification.setData(request.getData());
        notification.setDeduplicationKey(request.getDeduplicationKey());
        notification.setStatus(NotificationConstants.STATUS_PENDING);

        if (request.getDeduplicationKey() == null || request.getDeduplicationKey().isBlank()) {
            Notification saved = notificationRepository.saveAndFlush(notification);
            log.info("✅ Created notification {} for user {}", saved.getId(), saved.getUserId());
            return stored(saved);
        }

        int inserted = insertIfAbsent(notification);
        Notification saved = notificationRepository.findByDeduplicationKey(request.getDeduplicationKey())
                .orElseThrow(() -> new IllegalStateException(
                        "notification deduplication claim resolved without a committed row"));

        if (inserted == 1) {
            log.info("✅ Created notification {} for user {}", saved.getId(), saved.getUserId());
        }
        return stored(saved);
    }

    private int insertIfAbsent(Notification notification) {
        if (dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")) {
            return notificationRepository.insertIfAbsentH2(
                    notification.getUserId(), notification.getUserPrincipalId(), notification.getTitle(), notification.getMessage(),
                    notification.getType(), notification.getPriority(), notification.getStatus(),
                    notification.getIsRead(), notification.getRelatedEntityId(),
                    notification.getRelatedEntityType(), notification.getData(),
                    notification.getDeduplicationKey());
        }
        return notificationRepository.insertIfAbsentPostgres(
                notification.getUserId(), notification.getUserPrincipalId(), notification.getTitle(), notification.getMessage(),
                notification.getType(), notification.getPriority(), notification.getStatus(),
                notification.getIsRead(), notification.getRelatedEntityId(),
                notification.getRelatedEntityType(), notification.getData(),
                notification.getDeduplicationKey());
    }

    private StoredNotification<NotificationResponse> stored(Notification n) {
        return new StoredNotification<>(n.getId(), payload(n), n.getStatus(), notificationMapper.toResponse(n));
    }

    static ReplayPayload payload(Notification n) {
        return new ReplayPayload(n.getUserId(), n.getUserPrincipalId(), n.getTitle(), n.getMessage(),
                n.getType(), n.getPriority(), n.getRelatedEntityId(), n.getRelatedEntityType(), n.getData());
    }

    private static ReplayPayload payload(SendNotificationRequest r) {
        return new ReplayPayload(r.getUserId(), r.getUserPrincipalId(), r.getTitle(), r.getMessage(),
                r.getType(), r.getPriority(), r.getRelatedEntityId(), r.getRelatedEntityType(), r.getData());
    }

    private Inbox<Notification, NotificationResponse> inbox() {
        return new Inbox<>(
                new NotificationInboxAdapter(notificationRepository, notificationMapper, meterRegistry), LocalDateTime::now);
    }

    private InboxActor actor(Long userId) {
        return new InboxActor(null, userId, true, false);
    }

    private InboxActor actor(Long principalId, Long legacyUserId) {
        return new InboxActor(principalId, legacyUserId, false, principalOwnershipEnforced);
    }

    @Override
    public List<NotificationResponse> getUserNotifications(Long userId) {
        return inbox().list(actor(userId), false);
    }

    @Override
    public List<NotificationResponse> getUserNotifications(Long principalId, Long legacyUserId) {
        return inbox().list(actor(principalId, legacyUserId), false);
    }

    @Override
    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return inbox().list(actor(userId), true);
    }

    @Override
    public List<NotificationResponse> getUnreadNotifications(Long principalId, Long legacyUserId) {
        return inbox().list(actor(principalId, legacyUserId), true);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        return inbox().markRead(notificationId, actor(userId));
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long principalId, Long legacyUserId) {
        return inbox().markRead(notificationId, actor(principalId, legacyUserId));
    }

    @Override
    @Transactional
    public int markAllAsRead(Long userId) {
        return inbox().markAllRead(actor(userId));
    }

    @Override
    @Transactional
    public int markAllAsRead(Long principalId, Long legacyUserId) {
        return inbox().markAllRead(actor(principalId, legacyUserId));
    }

    @Override
    public long getUnreadCount(Long userId) {
        return inbox().unreadCount(actor(userId));
    }

    @Override
    public long getUnreadCount(Long principalId, Long legacyUserId) {
        return inbox().unreadCount(actor(principalId, legacyUserId));
    }

    @Override
    public NotificationResponse getNotificationById(Long id, Long userId) {
        return inbox().get(id, actor(userId));
    }

    @Override
    public NotificationResponse getNotificationById(Long id, Long principalId, Long legacyUserId) {
        return inbox().get(id, actor(principalId, legacyUserId));
    }

    @Override
    @Transactional
    public void deleteNotification(Long id, Long userId) {
        inbox().delete(id, actor(userId));
    }

    @Override
    @Transactional
    public void deleteNotification(Long id, Long principalId, Long legacyUserId) {
        inbox().delete(id, actor(principalId, legacyUserId));
    }

    @Override
    public void sendOrderCreatedNotification(UUID eventId, Long userId, Long orderId, String restaurantName) {
        sendOrderCreatedNotification(eventId, userId, null, orderId, restaurantName);
    }

    @Override
    public void sendOrderCreatedNotification(UUID eventId, Long userId, Long userPrincipalId, Long orderId, String restaurantName) {
        events().orderCreated(eventId, userId, userPrincipalId, orderId, restaurantName);
    }

    @Override
    public void sendDeliveryStatusNotification(UUID eventId, Long userId, Long deliveryId, String status, String shipperName) {
        sendDeliveryStatusNotification(eventId, userId, null, deliveryId, status, shipperName);
    }

    @Override
    public void sendDeliveryStatusNotification(UUID eventId, Long userId, Long userPrincipalId, Long deliveryId, String status, String shipperName) {
        events().deliveryStatus(eventId, userId, userPrincipalId, deliveryId, status, shipperName);
    }

    @Override
    public void sendShipperMatchFoundNotification(Long shipperId, Long orderId, String restaurantName,
            String pickupAddress, String deliveryAddress,
            Double distance, String offerEventId) {
        events().shipperOffer(shipperId, orderId, restaurantName,
                pickupAddress, deliveryAddress, distance, offerEventId);
        log.info("🎯 Sent match found notification to shipper {}", shipperId);
    }

    private EventNotifications events() {
        return new EventNotifications(intent -> sendNotification(toRequest(intent)));
    }

    private SendNotificationRequest toRequest(NotificationIntent intent) {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(intent.userId());
        request.setUserPrincipalId(intent.userPrincipalId());
        request.setTitle(intent.title());
        request.setMessage(intent.message());
        request.setType(intent.type());
        request.setPriority(intent.priority());
        request.setRelatedEntityId(intent.relatedEntityId());
        request.setRelatedEntityType(intent.relatedEntityType());
        request.setDeduplicationKey(intent.deduplicationKey());
        request.setSendPush(intent.sendPush());
        if (intent.data() != null) {
            Map<String, Object> data = new HashMap<>();
            data.putAll(intent.data());
            request.setData(new Gson().toJson(data));
        }
        return request;
    }
}
