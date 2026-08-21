package com.delivery.auth_service.service;

import com.delivery.auth_service.dto.RegistrationStatusResponse;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.IdentityRegistration;
import com.delivery.auth_service.exception.ResourceNotFoundException;
import com.delivery.auth_service.repository.IdentityRegistrationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;

/** Opaque recovery handle only; normal registration continues synchronously to User. */
@Service
public class IdentityRegistrationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final IdentityRegistrationRepository registrations;
    private final int expiredHandleRetentionDays;

    /** Compatibility constructor for existing direct unit fixtures. */
    public IdentityRegistrationService(IdentityRegistrationRepository registrations) {
        this(registrations, 1);
    }

    @Autowired
    public IdentityRegistrationService(
            IdentityRegistrationRepository registrations,
            @Value("${app.identity.registration.handle-retention-days:1}") int expiredHandleRetentionDays) {
        this.registrations = registrations;
        this.expiredHandleRetentionDays = Math.max(0, expiredHandleRetentionDays);
    }

    @Transactional
    public IssuedHandle issue(AuthAccount account) {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        IdentityRegistration registration = new IdentityRegistration();
        registration.setAccount(account); registration.setHandleHash(hash(raw)); registration.setExpiresAt(expiresAt);
        registrations.save(registration);
        return new IssuedHandle(raw, expiresAt);
    }

    @Transactional(readOnly = true)
    public RegistrationStatusResponse status(String rawHandle) {
        IdentityRegistration registration = registrations.findByHandleHash(hash(rawHandle))
                .orElseThrow(() -> new ResourceNotFoundException("Registration", "handle", "not found"));
        if (!registration.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Registration handle expired");
        }
        AuthAccount account = registration.getAccount();
        return new RegistrationStatusResponse(
                account.getId(),
                account.getLifecycleStatus(),
                nextAction(account),
                account.getUserId() != null,
                registration.getExpiresAt());
    }

    @Scheduled(cron = "${app.identity.registration.handle-cleanup-cron:0 45 3 * * *}")
    @Transactional
    public void cleanupExpiredHandles() {
        registrations.deleteExpiredBefore(LocalDateTime.now().minusDays(expiredHandleRetentionDays));
    }

    private static String nextAction(AuthAccount account) {
        return switch (account.getLifecycleStatus()) {
            case PENDING_PROFILE -> "CREATE_PROFILE";
            case PENDING_EMAIL_VERIFICATION -> "VERIFY_EMAIL";
            case ACTIVE -> "LOGIN";
            case BLOCKED -> "CONTACT_SUPPORT";
        };
    }
    private static String hash(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Registration handle is required");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    public record IssuedHandle(String rawHandle, LocalDateTime expiresAt) { }
}
