package com.delivery.auth_service.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String email;
    private String password;
    private String role; // <- phải là String để nhận từ API Gateway
    // private String deviceId;
    // private String deviceName;
    // private String deviceType;
    // private String ipAddress;
}
