package com.delivery.auth_service.dto;

public record FirebaseChatTokenResponse(
        String token,
        long expiresInSeconds,
        long principalId,
        String role) {
}
