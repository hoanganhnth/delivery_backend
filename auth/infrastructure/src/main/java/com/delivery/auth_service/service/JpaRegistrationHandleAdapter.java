package com.delivery.auth_service.service;

import com.delivery.auth.application.api.RegistrationHandlePort;
import com.delivery.auth_service.entity.IdentityRegistration;
import com.delivery.auth_service.repository.IdentityRegistrationRepository;
import com.delivery.auth_service.repository.AuthAccountRepository;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaRegistrationHandleAdapter implements RegistrationHandlePort {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final IdentityRegistrationRepository registrations;
    private final AuthAccountRepository accounts;
    @Override @Transactional public String issue(Long accountId, LocalDateTime expiresAt) {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        IdentityRegistration registration = new IdentityRegistration();
        registration.setAccount(accounts.getReferenceById(accountId));
        registration.setHandleHash(hash(raw)); registration.setExpiresAt(expiresAt);
        registrations.save(registration);
        return raw;
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable", unavailable); }
    }
}
