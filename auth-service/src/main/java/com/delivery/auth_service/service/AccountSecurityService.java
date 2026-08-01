package com.delivery.auth_service.service;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.AuthSecurityToken;
import com.delivery.auth_service.entity.AuthSecurityToken.Purpose;
import com.delivery.auth_service.exception.InvalidTokenException;
import com.delivery.auth_service.exception.ProvisioningConflictException;
import com.delivery.auth_service.dto.UserProvisioningIdentityResponse;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.AuthSecurityAuditRepository;
import com.delivery.auth_service.repository.AuthSecurityTokenRepository;
import com.delivery.auth_service.repository.AuthSessionRepository;
import com.delivery.auth_service.repository.RefreshTokenRecordRepository;
import com.delivery.auth_service.entity.RefreshTokenRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AccountSecurityService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UNIFORM_REQUEST_AUDIT_ACTION = "PASSWORD_RESET_REQUEST";
    private final AuthAccountRepository accounts;
    private final AuthSecurityTokenRepository tokens;
    private final AuthSecurityAuditRepository audits;
    private final AuthSessionRepository sessions;
    private final RefreshTokenRecordRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final SecurityAuditService auditService;
    private final Duration resetTtl;
    private final Duration verificationTtl;
    private final Duration userProvisioningTtl;
    private final int tokenRetentionDays;
    private final int auditRetentionDays;

    public AccountSecurityService(
            AuthAccountRepository accounts,
            AuthSecurityTokenRepository tokens,
            AuthSecurityAuditRepository audits,
            AuthSessionRepository sessions,
            RefreshTokenRecordRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events,
            SecurityAuditService auditService,
            @Value("${app.security-token.password-reset-ttl:PT15M}") Duration resetTtl,
            @Value("${app.security-token.email-verification-ttl:PT24H}") Duration verificationTtl,
            @Value("${app.security-token.user-provisioning-ttl:PT15M}") Duration userProvisioningTtl,
            @Value("${app.security-token.retention-days:30}") int tokenRetentionDays,
            @Value("${app.security-audit.retention-days:180}") int auditRetentionDays) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.audits = audits;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.auditService = auditService;
        this.resetTtl = requirePositive(resetTtl, "password reset TTL");
        this.verificationTtl = requirePositive(verificationTtl, "email verification TTL");
        this.userProvisioningTtl = requirePositive(userProvisioningTtl, "user provisioning TTL");
        this.tokenRetentionDays = Math.max(1, tokenRetentionDays);
        this.auditRetentionDays = Math.max(30, auditRetentionDays);
    }

    @Transactional
    public void requestPasswordReset(String email, String clientIp) {
        String normalized = normalizeEmail(email);
        AuthAccount account = accounts.findByEmail(normalized).orElse(null);
        if (account == null || !Boolean.TRUE.equals(account.getIsActive())) {
            auditService.recordTransactional(
                    null, UNIFORM_REQUEST_AUDIT_ACTION, "ACCEPTED", normalized, clientIp);
            return;
        }
        issueAndSend(account, Purpose.PASSWORD_RESET, resetTtl, clientIp);
    }

    @Transactional
    public void requestEmailVerification(String email, String clientIp) {
        String normalized = normalizeEmail(email);
        AuthAccount account = accounts.findByEmail(normalized).orElse(null);
        if (account == null || !Boolean.TRUE.equals(account.getIsActive())
                || account.getEmailVerifiedAt() != null) {
            auditService.recordTransactional(account == null ? null : account.getId(),
                    "EMAIL_VERIFICATION_REQUEST", "ACCEPTED", normalized, clientIp);
            return;
        }
        issueAndSend(account, Purpose.EMAIL_VERIFICATION, verificationTtl, clientIp);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword, String clientIp) {
        AuthSecurityToken token = requireUsable(rawToken, Purpose.PASSWORD_RESET, clientIp);
        LocalDateTime now = LocalDateTime.now();
        AuthAccount account = token.getAuthAccount();
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accounts.save(account);
        token.setConsumedAt(now);
        tokens.save(token);
        tokens.consumeOutstanding(account.getId(), Purpose.PASSWORD_RESET, now);
        refreshTokens.revokeAccount(
                account.getId(), RefreshTokenRecord.State.REVOKED, now);
        sessions.deactivateAllActiveSessions(account.getId(), now);
        auditService.recordTransactional(
                account.getId(), "PASSWORD_RESET_COMPLETE", "SUCCESS", null, clientIp);
    }

    @Transactional
    public void verifyEmail(String rawToken, String clientIp) {
        AuthSecurityToken token = requireUsable(rawToken, Purpose.EMAIL_VERIFICATION, clientIp);
        LocalDateTime now = LocalDateTime.now();
        AuthAccount account = token.getAuthAccount();
        account.setEmailVerifiedAt(now);
        account.setEmailVerificationRequired(false);
        accounts.save(account);
        token.setConsumedAt(now);
        tokens.save(token);
        tokens.consumeOutstanding(account.getId(), Purpose.EMAIL_VERIFICATION, now);
        auditService.recordTransactional(
                account.getId(), "EMAIL_VERIFICATION_COMPLETE", "SUCCESS", null, clientIp);
    }

    @Transactional
    public String issueUserProvisioning(AuthAccount account, String clientIp) {
        if (account == null || account.getId() == null
                || !Boolean.TRUE.equals(account.getIsActive())) {
            throw new IllegalArgumentException("Active auth account is required for user provisioning");
        }

        LocalDateTime now = LocalDateTime.now();
        tokens.consumeOutstanding(account.getId(), Purpose.USER_PROVISIONING, now);
        String rawToken = randomToken();
        AuthSecurityToken token = new AuthSecurityToken();
        token.setAuthAccount(account);
        token.setPurpose(Purpose.USER_PROVISIONING);
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(now.plus(userProvisioningTtl));
        tokens.saveAndFlush(token);
        auditService.recordTransactional(
                account.getId(), "USER_PROVISIONING_ISSUED", "SUCCESS", null, clientIp);
        return rawToken;
    }

    @Transactional
    public UserProvisioningIdentityResponse resolveUserProvisioning(String rawToken) {
        AuthSecurityToken token = requireUsableUserProvisioning(rawToken, true);
        AuthAccount account = token.getAuthAccount();
        return new UserProvisioningIdentityResponse(
                account.getId(), account.getEmail(), account.getRole().name());
    }

    @Transactional
    public void completeUserProvisioning(String rawToken, Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Positive userId is required");
        }

        AuthSecurityToken token = requireUsableUserProvisioning(rawToken, true);
        AuthAccount account = token.getAuthAccount();
        if (account.getUserId() != null && !account.getUserId().equals(userId)) {
            throw new ProvisioningConflictException(
                    "Auth identity is already linked to a different user");
        }

        if (token.getConsumedAt() != null) {
            if (userId.equals(account.getUserId())) {
                return;
            }
            throw new InvalidTokenException("Invalid or expired security token");
        }

        LocalDateTime now = LocalDateTime.now();
        account.setUserId(userId);
        accounts.save(account);
        token.setConsumedAt(now);
        tokens.save(token);
        tokens.consumeOutstanding(account.getId(), Purpose.USER_PROVISIONING, now);
        auditService.recordTransactional(
                account.getId(), "USER_PROVISIONING_COMPLETE", "SUCCESS", null, null);
    }

    private void issueAndSend(AuthAccount account, Purpose purpose, Duration ttl, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        tokens.consumeOutstanding(account.getId(), purpose, now);
        String rawToken = randomToken();
        AuthSecurityToken token = new AuthSecurityToken();
        token.setAuthAccount(account);
        token.setPurpose(purpose);
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(now.plus(ttl));
        tokens.saveAndFlush(token);

        String action = purpose == Purpose.PASSWORD_RESET
                ? UNIFORM_REQUEST_AUDIT_ACTION : "EMAIL_VERIFICATION_REQUEST";
        events.publishEvent(new SecurityEmailEvent(
                account.getId(), account.getEmail(), purpose, rawToken, clientIp));
        auditService.recordTransactional(
                account.getId(), action, "QUEUED", account.getEmail(), clientIp);
    }

    private AuthSecurityToken requireUsable(String rawToken, Purpose expected, String clientIp) {
        if (rawToken == null || rawToken.isBlank()) return reject(expected, "INVALID", clientIp);
        AuthSecurityToken token = tokens.findByTokenHashForUpdate(hashToken(rawToken)).orElse(null);
        if (token == null) return reject(expected, "INVALID", clientIp);
        if (token.getPurpose() != expected) return reject(expected, "WRONG_PURPOSE", clientIp);
        if (token.getConsumedAt() != null) return reject(expected, "REUSED", clientIp);
        if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            return reject(expected, "EXPIRED", clientIp);
        }
        if (!Boolean.TRUE.equals(token.getAuthAccount().getIsActive())) {
            return reject(expected, "INACTIVE_ACCOUNT", clientIp);
        }
        return token;
    }

    private AuthSecurityToken requireUsableUserProvisioning(
            String rawToken, boolean allowCompletedRetry) {
        if (rawToken == null || rawToken.isBlank()) {
            return reject(Purpose.USER_PROVISIONING, "INVALID", null);
        }
        AuthSecurityToken token = tokens.findByTokenHashForUpdate(hashToken(rawToken)).orElse(null);
        if (token == null) {
            return reject(Purpose.USER_PROVISIONING, "INVALID", null);
        }
        if (token.getPurpose() != Purpose.USER_PROVISIONING) {
            return reject(Purpose.USER_PROVISIONING, "WRONG_PURPOSE", null);
        }
        if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            return reject(Purpose.USER_PROVISIONING, "EXPIRED", null);
        }
        if (!Boolean.TRUE.equals(token.getAuthAccount().getIsActive())) {
            return reject(Purpose.USER_PROVISIONING, "INACTIVE_ACCOUNT", null);
        }
        if (token.getConsumedAt() != null
                && (!allowCompletedRetry || token.getAuthAccount().getUserId() == null)) {
            return reject(Purpose.USER_PROVISIONING, "REUSED", null);
        }
        return token;
    }

    private AuthSecurityToken reject(Purpose purpose, String outcome, String clientIp) {
        auditService.record(null, purpose.name() + "_CONSUME", outcome, null, clientIp);
        throw new InvalidTokenException("Invalid or expired security token");
    }

    @Scheduled(cron = "${app.security-token.cleanup-cron:0 35 3 * * *}")
    @Transactional
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        tokens.deleteExpiredBefore(now.minusDays(tokenRetentionDays));
        audits.deleteOlderThan(now.minusDays(auditRetentionDays));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) return "";
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private Duration requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }
}
