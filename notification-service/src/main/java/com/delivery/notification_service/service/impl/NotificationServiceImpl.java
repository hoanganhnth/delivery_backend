package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.common.constants.NotificationConstants;
import com.delivery.notification.domain.NotificationIntent;
import com.delivery.notification.domain.NotificationMapping;
import com.delivery.notification.domain.ReplayPayload;
import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.dto.response.NotificationResponse;
import com.delivery.notification_service.entity.Notification;
import com.delivery.notification_service.exception.NotificationNotFoundException;
import com.delivery.notification_service.exception.NotificationConflictException;
import com.delivery.notification_service.mapper.NotificationMapper;
import com.delivery.notification_service.repository.NotificationRepository;
import com.delivery.notification_service.service.*;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Counter;
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
        validateSendNotificationRequest(request);
        if (request.getDeduplicationKey() != null && !request.getDeduplicationKey().isBlank()) {
            Notification existing = notificationRepository
                    .findByDeduplicationKey(request.getDeduplicationKey())
                    .orElse(null);
            if (existing != null) {
                assertReplayMatches(existing, request);
                NotificationResponse stored = notificationMapper.toResponse(existing);
                if (NotificationConstants.STATUS_PENDING.equals(existing.getStatus())) {
                    log.info("Retrying pending notification event {} with stable id {}",
                            request.getDeduplicationKey(), existing.getId());
                    deliverAndMarkSent(request, stored);
                } else {
                    log.info("Skipping completed duplicate notification event {}",
                            request.getDeduplicationKey());
                }
                return stored;
            }
        }

        // The PENDING row must commit before external I/O. The atomic insert
        // also makes parallel Kafka partitions converge to one stable delivery
        // record; the coordinator below owns the later PENDING -> SENT lock.
        NotificationResponse notification = createNotification(request);

        deliverAndMarkSent(request, notification);

        log.info("📤 Successfully sent notification {} to user {}", notification.getId(), notification.getUserId());
        return notification;
    }

    private void deliverAndMarkSent(SendNotificationRequest request, NotificationResponse notification) {
        deliveryCoordinator.deliverPending(request, notification);
        notification.setStatus(NotificationConstants.STATUS_SENT);
    }

    private NotificationResponse createNotification(SendNotificationRequest request) {
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
            return notificationMapper.toResponse(saved);
        }

        int inserted = insertIfAbsent(notification);
        Notification saved = notificationRepository.findByDeduplicationKey(request.getDeduplicationKey())
                .orElseThrow(() -> new IllegalStateException(
                        "notification deduplication claim resolved without a committed row"));
        assertReplayMatches(saved, request);

        if (inserted == 1) {
            log.info("✅ Created notification {} for user {}", saved.getId(), saved.getUserId());
        }
        return notificationMapper.toResponse(saved);
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

    private void assertReplayMatches(Notification existing, SendNotificationRequest request) {
        ReplayPayload stored = new ReplayPayload(
                existing.getUserId(), existing.getUserPrincipalId(), existing.getTitle(), existing.getMessage(),
                existing.getType(), existing.getPriority(), existing.getRelatedEntityId(),
                existing.getRelatedEntityType(), existing.getData());
        ReplayPayload incoming = new ReplayPayload(
                request.getUserId(), request.getUserPrincipalId(), request.getTitle(), request.getMessage(),
                request.getType(), request.getPriority(), request.getRelatedEntityId(),
                request.getRelatedEntityType(), request.getData());
        boolean samePayload = stored.matches(incoming);
        if (!samePayload) {
            throw new NotificationConflictException(
                    "Deduplication key is already bound to a different notification payload");
        }
    }

    @Override
    public List<NotificationResponse> getUserNotifications(Long userId) {
        requirePositiveId(userId, "userId");
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, 100));
        return notificationMapper.toResponseList(notifications);
    }

    @Override
    public List<NotificationResponse> getUserNotifications(Long principalId, Long legacyUserId) {
        requireIdentity(principalId, legacyUserId);
        List<Notification> notifications = principalOwnershipEnforced
                ? notificationRepository.findByUserPrincipalIdOrderByCreatedAtDesc(principalId, PageRequest.of(0, 100))
                : notificationRepository.findByPrincipalOrUnmigratedLegacyUser(
                        principalId, legacyUserId, PageRequest.of(0, 100));
        if (!principalOwnershipEnforced) recordLegacyFallback(notifications, "inbox_list");
        return notificationMapper.toResponseList(notifications);
    }

    @Override
    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        requirePositiveId(userId, "userId");
        List<Notification> notifications = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(
                userId, false, PageRequest.of(0, 100));
        return notificationMapper.toResponseList(notifications);
    }

    @Override
    public List<NotificationResponse> getUnreadNotifications(Long principalId, Long legacyUserId) {
        requireIdentity(principalId, legacyUserId);
        List<Notification> notifications = principalOwnershipEnforced
                ? notificationRepository.findByUserPrincipalIdAndIsReadOrderByCreatedAtDesc(
                        principalId, false, PageRequest.of(0, 100))
                : notificationRepository.findUnreadByPrincipalOrUnmigratedLegacyUser(
                        principalId, legacyUserId, false, PageRequest.of(0, 100));
        if (!principalOwnershipEnforced) recordLegacyFallback(notifications, "inbox_unread_list");
        return notificationMapper.toResponseList(notifications);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        requirePositiveId(notificationId, "notificationId");
        requirePositiveId(userId, "userId");
        LocalDateTime readAt = LocalDateTime.now();
        int updated = notificationRepository.markAsRead(notificationId, userId, readAt);

        if (updated > 0) {
            Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                    .orElseThrow(() -> new NotificationNotFoundException(notificationId));

            log.info("👁️ Marked notification {} as read", notificationId);
            return notificationMapper.toResponse(notification);
        }

        Notification existing = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        return notificationMapper.toResponse(existing);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long principalId, Long legacyUserId) {
        requirePositiveId(notificationId, "notificationId"); requireIdentity(principalId, legacyUserId);
        Notification notification = findOwnedNotification(notificationId, principalId, legacyUserId);
        if (!principalOwnershipEnforced) recordLegacyFallback(notification, "inbox_mark_read");
        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true); notification.setReadAt(LocalDateTime.now()); notificationRepository.save(notification);
        }
        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public int markAllAsRead(Long userId) {
        requirePositiveId(userId, "userId");
        LocalDateTime readAt = LocalDateTime.now();
        int updated = notificationRepository.markAllAsReadByUser(userId, readAt);

        log.info("👁️ Marked {} notifications as read for user {}", updated, userId);
        return updated;
    }

    @Override
    @Transactional
    public int markAllAsRead(Long principalId, Long legacyUserId) {
        requireIdentity(principalId, legacyUserId);
        List<Notification> unread = principalOwnershipEnforced
                ? notificationRepository.findByUserPrincipalIdAndIsReadOrderByCreatedAtDesc(
                        principalId, false, PageRequest.of(0, 100))
                : notificationRepository.findUnreadByPrincipalOrUnmigratedLegacyUser(
                        principalId, legacyUserId, false, PageRequest.of(0, 100));
        if (!principalOwnershipEnforced) recordLegacyFallback(unread, "inbox_mark_all_read");
        LocalDateTime now = LocalDateTime.now(); unread.forEach(n -> { n.setIsRead(true); n.setReadAt(now); });
        notificationRepository.saveAll(unread); return unread.size();
    }

    @Override
    public long getUnreadCount(Long userId) {
        requirePositiveId(userId, "userId");
        return notificationRepository.countByUserIdAndIsRead(userId, false);
    }

    @Override
    public long getUnreadCount(Long principalId, Long legacyUserId) {
        requireIdentity(principalId, legacyUserId);
        return principalOwnershipEnforced
                ? notificationRepository.countByUserPrincipalIdAndIsRead(principalId, false)
                : notificationRepository.countByPrincipalOrUnmigratedLegacyUserAndIsRead(principalId, legacyUserId, false);
    }

    @Override
    public NotificationResponse getNotificationById(Long id, Long userId) {
        requirePositiveId(id, "notificationId");
        requirePositiveId(userId, "userId");
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        return notificationMapper.toResponse(notification);
    }

    @Override
    public NotificationResponse getNotificationById(Long id, Long principalId, Long legacyUserId) {
        requirePositiveId(id, "notificationId"); requireIdentity(principalId, legacyUserId);
        Notification notification = findOwnedNotification(id, principalId, legacyUserId);
        if (!principalOwnershipEnforced) recordLegacyFallback(notification, "inbox_read");
        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public void deleteNotification(Long id, Long userId) {
        requirePositiveId(id, "notificationId");
        requirePositiveId(userId, "userId");
        long deleted = notificationRepository.deleteByIdAndUserId(id, userId);
        if (deleted == 0) {
            throw new NotificationNotFoundException(id);
        }
        log.info("🗑️ Deleted notification {}", id);
    }

    @Override
    @Transactional
    public void deleteNotification(Long id, Long principalId, Long legacyUserId) {
        requirePositiveId(id, "notificationId"); requireIdentity(principalId, legacyUserId);
        Notification notification = findOwnedNotification(id, principalId, legacyUserId);
        if (!principalOwnershipEnforced) recordLegacyFallback(notification, "inbox_delete");
        notificationRepository.delete(notification);
    }

    private void requireIdentity(Long principalId, Long legacyUserId) {
        requirePositiveId(principalId, "principalId"); requirePositiveId(legacyUserId, "legacyUserId");
    }

    private Notification findOwnedNotification(Long id, Long principalId, Long legacyUserId) {
        return (principalOwnershipEnforced
                ? notificationRepository.findByIdAndUserPrincipalId(id, principalId)
                : notificationRepository.findByIdAndPrincipalOrUnmigratedLegacyUser(id, principalId, legacyUserId))
                .orElseThrow(() -> new NotificationNotFoundException(id));
    }

    private void recordLegacyFallback(Notification notification, String surface) {
        if (notification != null && notification.getUserPrincipalId() == null) {
            legacyFallbackCounter(surface).increment();
        }
    }

    private void recordLegacyFallback(List<Notification> notifications, String surface) {
        long count = notifications.stream().filter(notification -> notification.getUserPrincipalId() == null).count();
        if (count > 0) legacyFallbackCounter(surface).increment(count);
    }

    private Counter legacyFallbackCounter(String surface) {
        return Counter.builder("delivery.identity.legacy.fallback")
                .tag("service", "notification").tag("surface", surface).register(meterRegistry);
    }

    @Override
    public void sendOrderCreatedNotification(UUID eventId, Long userId, Long orderId, String restaurantName) {
        sendOrderCreatedNotification(eventId, userId, null, orderId, restaurantName);
    }

    @Override
    public void sendOrderCreatedNotification(UUID eventId, Long userId, Long userPrincipalId, Long orderId, String restaurantName) {
        sendNotification(toRequest(NotificationMapping.orderCreated(
                eventId, userId, userPrincipalId, orderId, restaurantName)));
    }

    @Override
    public void sendDeliveryStatusNotification(UUID eventId, Long userId, Long deliveryId, String status, String shipperName) {
        sendDeliveryStatusNotification(eventId, userId, null, deliveryId, status, shipperName);
    }

    @Override
    public void sendDeliveryStatusNotification(UUID eventId, Long userId, Long userPrincipalId, Long deliveryId, String status, String shipperName) {
        sendNotification(toRequest(NotificationMapping.deliveryStatus(
                eventId, userId, userPrincipalId, deliveryId, status, shipperName)));
    }

    @Override
    public void sendShipperMatchFoundNotification(Long shipperId, Long orderId, String restaurantName,
            String pickupAddress, String deliveryAddress,
            Double distance, String offerEventId) {
        sendNotification(toRequest(NotificationMapping.shipperOffer(shipperId, orderId, restaurantName,
                pickupAddress, deliveryAddress, distance, offerEventId)));
        log.info("🎯 Sent match found notification to shipper {}", shipperId);
    }

    private void validateSendNotificationRequest(SendNotificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Send notification request is required");
        }
        requirePositiveId(request.getUserId(), "userId");
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        if (request.getType() == null || request.getType().isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        if (request.getPriority() == null || request.getPriority().isBlank()) {
            throw new IllegalArgumentException("priority is required");
        }
    }

    private void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
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
