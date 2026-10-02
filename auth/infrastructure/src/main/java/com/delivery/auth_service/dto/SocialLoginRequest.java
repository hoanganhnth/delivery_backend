package com.delivery.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialLoginRequest {
    @NotBlank
    private String provider; // "google", "apple", "facebook"
    @NotBlank
    private String token;    // ID Token or Access Token from provider
    private String role;     // Optional, defaults to canonical public role "USER"
    @NotBlank
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String ipAddress;
}
