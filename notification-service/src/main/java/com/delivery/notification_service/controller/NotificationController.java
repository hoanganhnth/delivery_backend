package com.delivery.notification_service.controller;

import com.delivery.notification_service.common.constants.ApiPathConstants;
import com.delivery.notification_service.common.constants.HttpHeaderConstants;
import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.dto.response.NotificationResponse;
import com.delivery.notification_service.payload.BaseResponse;
import com.delivery.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ✅ Notification Controller theo Backend Instructions
 */
@Slf4j
@RestController
@RequestMapping(ApiPathConstants.NOTIFICATIONS)
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping(ApiPathConstants.SEND_NOTIFICATION)
    public ResponseEntity<BaseResponse<NotificationResponse>> sendNotification(
            @RequestBody SendNotificationRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        NotificationResponse response = notificationService.sendNotification(request);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Gửi thông báo thành công"));
    }

    @GetMapping(ApiPathConstants.USER_NOTIFICATIONS)
    public ResponseEntity<BaseResponse<List<NotificationResponse>>> getUserNotifications(
            @PathVariable Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long requestUserId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        List<NotificationResponse> notifications = notificationService.getUserNotifications(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, notifications, "Lấy danh sách thông báo thành công"));
    }

    @GetMapping("/unread")
    public ResponseEntity<BaseResponse<List<NotificationResponse>>> getUnreadNotifications(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {

        List<NotificationResponse> notifications = notificationService.getUnreadNotifications(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, notifications, "Lấy danh sách thông báo chưa đọc thành công"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<BaseResponse<Long>> getUnreadCount(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {

        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, count, "Lấy số lượng thông báo chưa đọc thành công"));
    }

    @PutMapping(ApiPathConstants.MARK_AS_READ)
    public ResponseEntity<BaseResponse<NotificationResponse>> markAsRead(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {

        NotificationResponse response = notificationService.markAsRead(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Đánh dấu đã đọc thành công"));
    }

    @PutMapping(ApiPathConstants.MARK_ALL_AS_READ)
    public ResponseEntity<BaseResponse<Integer>> markAllAsRead(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {

        int updated = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, updated, "Đánh dấu tất cả đã đọc thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<NotificationResponse>> getNotificationById(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {

        NotificationResponse response = notificationService.getNotificationById(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thông báo thành công"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteNotification(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {

        notificationService.deleteNotification(id);
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Xóa thông báo thành công"));
    }
}
