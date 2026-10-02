package com.delivery.auth_service.dto;

public class CreateUserRequest {
    private Long authId;
    private Long principalId;
    private String email;
    private String role;

    public CreateUserRequest() {
    }

    public CreateUserRequest(Long authId, String email, String role) {
        this(authId, authId, email, role);
    }

    public CreateUserRequest(Long authId, Long principalId, String email, String role) {
        this.authId = authId;
        this.principalId = principalId;
        this.email = email;
        this.role = role;
    }

    // Getters and setters
    public Long getAuthId() {
        return authId;
    }

    public void setAuthId(Long authId) {
        this.authId = authId;
    }

    public Long getPrincipalId() { return principalId; }
    public void setPrincipalId(Long principalId) { this.principalId = principalId; }

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
