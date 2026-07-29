package com.delivery.notification_service.controller;

import com.delivery.notification_service.common.constants.ApiPathConstants;
import com.delivery.notification_service.common.constants.HttpHeaderConstants;
import com.delivery.notification_service.common.constants.RoleConstants;
import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.dto.response.NotificationResponse;
import com.delivery.notification_service.exception.NotificationAccessDeniedException;
import com.delivery.notification_service.payload.BaseResponse;
import com.delivery.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Set;


/**
 * ✅ Notification Controller theo Backend Instructions
 */
@Slf4j
@RestController
@RequestMapping(ApiPathConstants.NOTIFICATIONS)
public class NotificationController {

    private static final Set<String> AUTHENTICATED_NOTIFICATION_ROLES = Set.of(
            RoleConstants.USER,
            RoleConstants.RESTAURANT_OWNER,
            RoleConstants.SHIPPER,
            RoleConstants.ADMIN
    );

    private final NotificationService notificationService;
    private final String internalSecret;

    public NotificationController(NotificationService notificationService,
                                  @Value("${app.internal.secret:}") String internalSecret) {
        this.notificationService = notificationService;
        this.internalSecret = internalSecret;
    }

    @PostMapping(ApiPathConstants.SEND_NOTIFICATION)
    public ResponseEntity<BaseResponse<NotificationResponse>> sendNotification(
            @Valid @RequestBody SendNotificationRequest request,
            @RequestHeader(value = HttpHeaderConstants.INTERNAL_TOKEN, required = false) String internalToken) {

        if (internalSecret == null || internalSecret.isBlank()
                || !internalSecret.equals(internalToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Forbidden"));
        }

        NotificationResponse response = notificationService.sendNotification(request);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Gửi thông báo thành công"));
    }

    @GetMapping(ApiPathConstants.USER_NOTIFICATIONS)
    public ResponseEntity<BaseResponse<List<NotificationResponse>>> getUserNotifications(
            @PathVariable Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long requestUserId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        requireAuthenticatedNotificationRole(role);
        requireSelf(userId, requestUserId);
        List<NotificationResponse> notifications = notificationService.getUserNotifications(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, notifications, "Lấy danh sách thông báo thành công"));
    }

    @GetMapping("/unread")
    public ResponseEntity<BaseResponse<List<NotificationResponse>>> getUnreadNotifications(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        requireAuthenticatedNotificationRole(role);
        List<NotificationResponse> notifications = notificationService.getUnreadNotifications(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, notifications, "Lấy danh sách thông báo chưa đọc thành công"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<BaseResponse<Long>> getUnreadCount(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        requireAuthenticatedNotificationRole(role);
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, count, "Lấy số lượng thông báo chưa đọc thành công"));
    }

    @PutMapping(ApiPathConstants.MARK_AS_READ)
    public ResponseEntity<BaseResponse<NotificationResponse>> markAsRead(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        requireAuthenticatedNotificationRole(role);
        NotificationResponse response = notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Đánh dấu đã đọc thành công"));
    }

    @PutMapping(ApiPathConstants.MARK_ALL_AS_READ)
    public ResponseEntity<BaseResponse<Integer>> markAllAsRead(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        requireAuthenticatedNotificationRole(role);
        int updated = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, updated, "Đánh dấu tất cả đã đọc thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<NotificationResponse>> getNotificationById(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        requireAuthenticatedNotificationRole(role);
        NotificationResponse response = notificationService.getNotificationById(id, userId);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thông báo thành công"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteNotification(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        requireAuthenticatedNotificationRole(role);
        notificationService.deleteNotification(id, userId);
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Xóa thông báo thành công"));
    }

    private void requireAuthenticatedNotificationRole(String actualRole) {
        if (actualRole == null || !AUTHENTICATED_NOTIFICATION_ROLES.contains(actualRole)) {
            throw new NotificationAccessDeniedException("Forbidden");
        }
    }

    private void requireSelf(Long pathUserId, Long authenticatedUserId) {
        if (!pathUserId.equals(authenticatedUserId)) {
            throw new NotificationAccessDeniedException("Cannot access another user's notifications");
        }
    }
}
