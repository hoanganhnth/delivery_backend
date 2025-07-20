package com.delivery.api_gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegisterRequest {
    private String email;
    private String password;
    private String role;
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String ipAddress;
}
