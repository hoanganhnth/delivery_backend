package com.delivery.api_gateway.dto;

import lombok.Data;

@Data
public class OrchestratorRegisterRequest {
    // Auth info
    private String email;
    private String password;
    private String role;

    // User profile
    private String fullName;
    private String phone;
    private String dob;
    private String address;

    // Device info (optional)
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String ipAddress;
}
