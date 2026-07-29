package com.delivery.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Email
    private String email;
    @NotBlank
    private String password;
    @NotBlank
    private String role; // <- phải là String để nhận từ API Gateway
    // private String deviceId;
    // private String deviceName;
    // private String deviceType;
    // private String ipAddress;
}
