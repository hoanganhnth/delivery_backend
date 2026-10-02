package com.delivery.auth.application.api;

import java.time.LocalDateTime;

/** Public registration handoff; the handle is recovery metadata, not a credential. */
public record RegistrationResult(
        AccountSnapshot account,
        String provisioningToken,
        String registrationHandle,
        LocalDateTime registrationHandleExpiresAt) {
}
