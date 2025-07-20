package com.delivery.api_gateway.dto;

import lombok.Data;

@Data
public class AuthResponse {
    private Long authId;
    private String email;
    private String role;
    private String accessToken;
    private String refreshToken;
}
