package com.delivery.user_service.dto;

import lombok.Data;

@Data
public class AuthProvisioningIdentityResponse {
    private Long authId;
    private String email;
    private String role;
}
