package com.delivery.auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SessionInfoResponse {
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String ipAddress;
    private LocalDateTime lastLoginAt;
    private LocalDateTime expiresAt;
    private Boolean isActive;
}
