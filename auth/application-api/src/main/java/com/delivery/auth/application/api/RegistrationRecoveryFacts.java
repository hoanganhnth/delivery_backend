package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;
import java.time.LocalDateTime;

public record RegistrationRecoveryFacts(AuthAccount account, LocalDateTime expiresAt) {}
