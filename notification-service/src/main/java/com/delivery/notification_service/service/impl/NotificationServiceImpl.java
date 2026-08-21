package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.common.constants.NotificationConstants;
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
import java.util.Objects;
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
        boolean samePayload = Objects.equals(existing.getUserId(), request.getUserId())
                && Objects.equals(existing.getUserPrincipalId(), request.getUserPrincipalId())
                && Objects.equals(existing.getTitle(), request.getTitle())
                && Objects.equals(existing.getMessage(), request.getMessage())
                && Objects.equals(existing.getType(), request.getType())
                && Objects.equals(existing.getPriority(), request.getPriority())
                && Objects.equals(existing.getRelatedEntityId(), request.getRelatedEntityId())
                && Objects.equals(existing.getRelatedEntityType(), request.getRelatedEntityType())
                && Objects.equals(existing.getData(), request.getData());
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
        requireEventId(eventId);
        requirePositiveId(userId, "userId");
        requirePositiveId(orderId, "orderId");
        if (restaurantName == null || restaurantName.isBlank()) {
            throw new IllegalArgumentException("canonical restaurantName is required");
        }
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(userId);
        request.setUserPrincipalId(userPrincipalId);
        request.setTitle("Đơn hàng đã được tạo");
        request.setMessage("Đơn hàng #" + orderId + " từ " + restaurantName + " đã được tạo thành công");
        request.setType(NotificationConstants.ORDER_CREATED);
        request.setPriority(NotificationConstants.PRIORITY_MEDIUM);
        request.setRelatedEntityId(orderId);
        request.setRelatedEntityType("ORDER");
        request.setDeduplicationKey("order-created:" + eventId);

        sendNotification(request);
    }

    @Override
    public void sendDeliveryStatusNotification(UUID eventId, Long userId, Long deliveryId, String status, String shipperName) {
        sendDeliveryStatusNotification(eventId, userId, null, deliveryId, status, shipperName);
    }

    @Override
    public void sendDeliveryStatusNotification(UUID eventId, Long userId, Long userPrincipalId, Long deliveryId, String status, String shipperName) {
        requireEventId(eventId);
        requirePositiveId(userId, "userId");
        requirePositiveId(deliveryId, "deliveryId");
        String title = getDeliveryStatusTitle(status);
        String message = getDeliveryStatusMessage(deliveryId, status, shipperName);

        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(userId);
        request.setUserPrincipalId(userPrincipalId);
        request.setTitle(title);
        request.setMessage(message);
        request.setType(getDeliveryStatusType(status));
        request.setPriority(NotificationConstants.PRIORITY_HIGH);
        request.setRelatedEntityId(deliveryId);
        request.setRelatedEntityType("DELIVERY");
        request.setDeduplicationKey("delivery-status:" + eventId);

        sendNotification(request);
    }

    @Override
    public void sendShipperMatchFoundNotification(Long shipperId, Long orderId, String restaurantName,
            String pickupAddress, String deliveryAddress,
            Double distance, String offerEventId) {
        requirePositiveId(shipperId, "shipperId");
        requirePositiveId(orderId, "orderId");
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(shipperId);
        request.setTitle("🎯 Đơn hàng phù hợp!");
        request.setMessage(String.format(
                "Đơn hàng #%d từ %s - cách điểm lấy khoảng %.1fkm. Mở ứng dụng để xem offer hiện tại.",
                orderId, restaurantName, distance));
        request.setType(NotificationConstants.MATCH_FOUND);
        request.setPriority(NotificationConstants.PRIORITY_HIGH);
        request.setRelatedEntityId(orderId);
        request.setRelatedEntityType("ORDER");
        request.setDeduplicationKey("shipper-offer:" + offerEventId + ":" + shipperId);
        // Persist the inbox record and use FCM only as a best-effort wake-up;
        // Delivery's authenticated current-offer endpoint is the source of truth.
        request.setSendPush(true);
        // Add detailed info to data field
        Map<String, Object> data = new HashMap<>();
        data.put("pickupAddress", pickupAddress);
        data.put("deliveryAddress", deliveryAddress);
        data.put("distance", distance);
        data.put("orderId", orderId);
        data.put("recoveryEndpoint", "/api/deliveries/offers/current");
        Gson gson = new Gson();
        String json = gson.toJson(data);
        request.setData(json);
        sendNotification(request);
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

    private void requireEventId(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("stable eventId is required");
        }
    }

    private String getDeliveryStatusTitle(String status) {
        return switch (status) {
            case "PENDING" -> "Đơn đang chờ xử lý giao hàng";
            case "FINDING_SHIPPER" -> "Đang tìm shipper";
            case "WAIT_SHIPPER_CONFIRM" -> "Đang chờ shipper xác nhận";
            case "SHIPPER_NOT_FOUND" -> "Chưa tìm được shipper";
            case "ASSIGNED" -> "Đã phân công shipper";
            case "PICKED_UP" -> "Shipper đã lấy hàng";
            case "DELIVERING" -> "Đơn hàng đang được giao";
            case "DELIVERED" -> "Giao hàng hoàn thành";
            case "CANCELLED" -> "Giao hàng đã bị hủy";
            default -> throw new IllegalArgumentException("Unknown delivery status: " + status);
        };
    }

    private String getDeliveryStatusMessage(Long deliveryId, String status, String shipperName) {
        return switch (status) {
            case "PENDING" -> "Đơn hàng đang chờ bắt đầu quy trình giao";
            case "FINDING_SHIPPER" -> "Hệ thống đang tìm shipper cho đơn hàng của bạn";
            case "WAIT_SHIPPER_CONFIRM" -> "Đang chờ shipper xác nhận nhận đơn";
            case "SHIPPER_NOT_FOUND" -> "Hiện chưa tìm được shipper phù hợp cho đơn hàng";
            case "ASSIGNED" -> hasText(shipperName)
                    ? shipperName + " đã được phân công giao đơn hàng của bạn"
                    : "Đơn hàng của bạn đã được phân công cho shipper";
            case "PICKED_UP" -> hasText(shipperName)
                    ? shipperName + " đã lấy đơn hàng và chuẩn bị giao"
                    : "Đơn hàng của bạn đã được lấy và chuẩn bị giao";
            case "DELIVERING" -> hasText(shipperName)
                    ? shipperName + " đang trên đường giao hàng"
                    : "Đơn hàng của bạn đang được giao";
            case "DELIVERED" -> hasText(shipperName)
                    ? "Đơn hàng đã được " + shipperName + " giao thành công"
                    : "Đơn hàng đã được giao thành công";
            case "CANCELLED" -> "Quy trình giao hàng đã bị hủy";
            default -> throw new IllegalArgumentException("Unknown delivery status: " + status);
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String getDeliveryStatusType(String status) {
        return switch (status) {
            case "PENDING" -> NotificationConstants.DELIVERY_PENDING;
            case "FINDING_SHIPPER" -> NotificationConstants.DELIVERY_FINDING_SHIPPER;
            case "WAIT_SHIPPER_CONFIRM" -> NotificationConstants.DELIVERY_WAIT_SHIPPER_CONFIRM;
            case "SHIPPER_NOT_FOUND" -> NotificationConstants.DELIVERY_SHIPPER_NOT_FOUND;
            case "ASSIGNED" -> NotificationConstants.DELIVERY_ASSIGNED;
            case "PICKED_UP" -> NotificationConstants.DELIVERY_PICKED_UP;
            case "DELIVERING" -> NotificationConstants.DELIVERY_DELIVERING;
            case "DELIVERED" -> NotificationConstants.DELIVERY_DELIVERED;
            case "CANCELLED" -> NotificationConstants.DELIVERY_CANCELLED;
            default -> throw new IllegalArgumentException("Unknown delivery status: " + status);
        };
    }
}
