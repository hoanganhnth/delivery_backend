package com.delivery.user_service.dto;

public record AuthProvisioningCompleteRequest(String provisioningToken, Long userId) {
}
