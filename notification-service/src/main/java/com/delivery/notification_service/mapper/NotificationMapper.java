package com.delivery.notification_service.mapper;

import com.delivery.notification_service.dto.response.NotificationResponse;
import com.delivery.notification_service.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * ✅ Notification Mapper cho mapping giữa Entity và DTO theo Backend Instructions
 */
@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
    
    List<NotificationResponse> toResponseList(List<Notification> notifications);
    
    @Mapping(target = "creatorId", ignore = true)
    Notification toEntity(NotificationResponse response);
}
