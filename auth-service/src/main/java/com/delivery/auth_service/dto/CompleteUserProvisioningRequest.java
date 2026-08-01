package com.delivery.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CompleteUserProvisioningRequest {
    @NotBlank
    private String provisioningToken;

    @NotNull
    @Positive
    private Long userId;
}
