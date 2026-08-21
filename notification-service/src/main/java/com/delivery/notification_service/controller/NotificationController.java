package com.delivery.notification_service.controller;

import com.delivery.notification_service.common.constants.ApiPathConstants;
import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.dto.response.NotificationResponse;
import com.delivery.notification_service.exception.NotificationAccessDeniedException;
import com.delivery.notification_service.payload.BaseResponse;
import com.delivery.notification_service.service.NotificationService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@Slf4j
@RestController
@RequestMapping(ApiPathConstants.NOTIFICATIONS)
public class NotificationController {

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
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {

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
            @AuthenticationPrincipal AuthenticatedActor actor) {

        requireActor(actor);
        requireSelf(userId, actor.getLegacyUserId());
        List<NotificationResponse> notifications = notificationService.getUserNotifications(actor.getPrincipalId(), actor.getLegacyUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, notifications, "Lấy danh sách thông báo thành công"));
    }

    @GetMapping("/unread")
    public ResponseEntity<BaseResponse<List<NotificationResponse>>> getUnreadNotifications(
            @AuthenticationPrincipal AuthenticatedActor actor) {

        requireActor(actor);
        List<NotificationResponse> notifications = notificationService.getUnreadNotifications(actor.getPrincipalId(), actor.getLegacyUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, notifications, "Lấy danh sách thông báo chưa đọc thành công"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<BaseResponse<Long>> getUnreadCount(
            @AuthenticationPrincipal AuthenticatedActor actor) {

        requireActor(actor);
        long count = notificationService.getUnreadCount(actor.getPrincipalId(), actor.getLegacyUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, count, "Lấy số lượng thông báo chưa đọc thành công"));
    }

    @PutMapping(ApiPathConstants.MARK_AS_READ)
    public ResponseEntity<BaseResponse<NotificationResponse>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {

        requireActor(actor);
        NotificationResponse response = notificationService.markAsRead(id, actor.getPrincipalId(), actor.getLegacyUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Đánh dấu đã đọc thành công"));
    }

    @PutMapping(ApiPathConstants.MARK_ALL_AS_READ)
    public ResponseEntity<BaseResponse<Integer>> markAllAsRead(
            @AuthenticationPrincipal AuthenticatedActor actor) {

        requireActor(actor);
        int updated = notificationService.markAllAsRead(actor.getPrincipalId(), actor.getLegacyUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, updated, "Đánh dấu tất cả đã đọc thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<NotificationResponse>> getNotificationById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {

        requireActor(actor);
        NotificationResponse response = notificationService.getNotificationById(id, actor.getPrincipalId(), actor.getLegacyUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thông báo thành công"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {

        requireActor(actor);
        notificationService.deleteNotification(id, actor.getPrincipalId(), actor.getLegacyUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Xóa thông báo thành công"));
    }

    private void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.getPrincipalId() == null || actor.getLegacyUserId() == null
                || (!actor.isUser() && !actor.isShipper() && !actor.isShopOwner() && !actor.isAdmin())) {
            throw new NotificationAccessDeniedException("Forbidden");
        }
    }

    private void requireSelf(Long pathUserId, Long authenticatedUserId) {
        if (!pathUserId.equals(authenticatedUserId)) {
            throw new NotificationAccessDeniedException("Cannot access another user's notifications");
        }
    }
}
