package com.delivery.auth.application.api;

public record UserProfileProvisioningReply(int status, String message, Long userId,
        Long authId, String email, String role) {}
