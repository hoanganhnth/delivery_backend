package com.delivery.auth_service.dto;

import com.delivery.auth_service.entity.AuthAccount.Role;

public class RegisterRequest {
    private String email;
    private String password;
    private Role role;

    // Constructors
    public RegisterRequest() {
    }

    public RegisterRequest(String email, String password, Role role) {
        this.email = email;
        this.password = password;
        this.role = role;
    }

    // Getters & Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
