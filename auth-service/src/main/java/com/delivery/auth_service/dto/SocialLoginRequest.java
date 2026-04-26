package com.delivery.auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialLoginRequest {
    private String provider; // "google", "apple", "facebook"
    private String token;    // ID Token or Access Token from provider
    private String role;     // Optional, defaults to "CUSTOMER" if creating a new account
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String ipAddress;
}
