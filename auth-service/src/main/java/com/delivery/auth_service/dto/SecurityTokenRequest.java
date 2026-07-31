package com.delivery.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SecurityTokenRequest {
    @NotBlank
    @Size(min = 32, max = 256)
    private String token;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
