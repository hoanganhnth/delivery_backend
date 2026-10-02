package com.delivery.auth_service.service;

import com.delivery.auth.application.api.*;
import com.delivery.auth_service.repository.IdentityRegistrationRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaRegistrationRecoveryAdapter implements RegistrationRecoveryPort {
    private final IdentityRegistrationRepository registrations;
    @Override @Transactional(readOnly = true)
    public Optional<RegistrationRecoveryFacts> findByHandle(String rawHandle) {
        return registrations.findByHandleHash(TokenFingerprint.sha256(rawHandle))
                .map(row -> new RegistrationRecoveryFacts(AuthAccountMapping.toDomain(row.getAccount()), row.getExpiresAt()));
    }
    @Override @Transactional
    public void deleteExpiredBefore(LocalDateTime cutoff) { registrations.deleteExpiredBefore(cutoff); }
}
