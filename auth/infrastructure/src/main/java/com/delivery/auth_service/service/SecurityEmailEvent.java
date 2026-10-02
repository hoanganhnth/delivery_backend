package com.delivery.auth_service.service;

import com.delivery.auth_service.entity.AuthSecurityToken.Purpose;

/** In-memory post-commit event. Never persist or log this record: it carries the raw one-time token. */
public record SecurityEmailEvent(
        Long authId,
        String recipient,
        Purpose purpose,
        String rawToken,
        String clientIp) {

    @Override
    public String toString() {
        return "SecurityEmailEvent[authId=" + authId
                + ", purpose=" + purpose
                + ", sensitiveFields=REDACTED]";
    }
}
