package com.delivery.auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProvisioningIdentityResponse {
    private Long authId;
    private String email;
    private String role;
}
