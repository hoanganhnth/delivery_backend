package com.delivery.auth.application.api;

import java.time.LocalDateTime;

public interface RegistrationHandlePort {
    String issue(Long accountId, LocalDateTime expiresAt);
}
