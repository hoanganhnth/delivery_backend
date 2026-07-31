package com.delivery.auth_service.service;

import com.delivery.auth_service.entity.AuthSecurityToken.Purpose;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEmailEventTest {
    @Test
    void stringRepresentationNeverExposesDeliverySecretsOrPersonalData() {
        SecurityEmailEvent event = new SecurityEmailEvent(
                42L,
                "user@example.com",
                Purpose.PASSWORD_RESET,
                "raw-reset-token",
                "192.0.2.10");

        assertThat(event.toString())
                .contains("authId=42", "purpose=PASSWORD_RESET", "sensitiveFields=REDACTED")
                .doesNotContain("user@example.com", "raw-reset-token", "192.0.2.10");
    }
}
