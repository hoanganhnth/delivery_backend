package com.delivery.user_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Trusted Auth-to-User projection command. It is accepted only with the
 * service credential; the admin identity is audit data supplied by Auth.
 */
public record UserBlockStatusRequest(
        @NotNull @Positive Long adminId,
        @NotNull Boolean blocked,
        @Size(max = 500) String reason) {
}
