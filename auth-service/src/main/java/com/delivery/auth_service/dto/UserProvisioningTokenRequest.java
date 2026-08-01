package com.delivery.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserProvisioningTokenRequest {
    @NotBlank
    private String provisioningToken;
}
