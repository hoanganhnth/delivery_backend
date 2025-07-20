package com.delivery.auth_service.dto;

import lombok.Data;

@Data
public class AuthRegisterResponse {
    private Long authId;
    private String email;
    private String role;
}
