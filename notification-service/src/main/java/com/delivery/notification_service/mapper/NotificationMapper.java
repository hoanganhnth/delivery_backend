package com.delivery.notification_service.mapper;

import com.delivery.notification_service.dto.response.NotificationResponse;
import com.delivery.notification_service.entity.Notification;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ✅ Notification Mapper cho mapping giữa Entity và DTO theo Backend Instructions
 */
@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        if (notification == null) {
            return null;
        }
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setUserId(notification.getUserId());
        response.setTitle(notification.getTitle());
        response.setMessage(notification.getMessage());
        response.setType(notification.getType());
        response.setPriority(notification.getPriority());
        response.setStatus(notification.getStatus());
        response.setIsRead(notification.getIsRead());
        response.setRelatedEntityId(notification.getRelatedEntityId());
        response.setRelatedEntityType(notification.getRelatedEntityType());
        response.setData(notification.getData());
        response.setSentAt(notification.getSentAt());
        response.setReadAt(notification.getReadAt());
        response.setCreatedAt(notification.getCreatedAt());
        response.setUpdatedAt(notification.getUpdatedAt());
        return response;
    }

    public List<NotificationResponse> toResponseList(List<Notification> notifications) {
        if (notifications == null) {
            return Collections.emptyList();
        }
        return notifications.stream().map(this::toResponse).collect(Collectors.toList());
    }

}
