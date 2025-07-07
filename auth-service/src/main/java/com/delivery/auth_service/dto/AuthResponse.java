package com.delivery.auth_service.dto;

public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long authId;
    private String email;
    private String role;

    public AuthResponse() {
    }

    public AuthResponse(String accessToken, String refreshToken, Long authId, String email, String role) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.authId = authId;
        this.email = email;
        this.role = role;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getAuthId() {
        return authId;
    }

    public void setAuthId(Long authId) {
        this.authId = authId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
