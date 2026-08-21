package com.delivery.auth_service.service;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.AuthSecurityToken;
import com.delivery.auth_service.entity.AuthSecurityToken.Purpose;
import com.delivery.auth_service.exception.InvalidTokenException;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.AuthSecurityAuditRepository;
import com.delivery.auth_service.repository.AuthSecurityTokenRepository;
import com.delivery.auth_service.repository.AuthSessionRepository;
import com.delivery.auth_service.repository.RefreshTokenRecordRepository;
import com.delivery.auth_service.entity.RefreshTokenRecord;
import com.delivery.identity.contracts.IdentityLifecycleStatus;
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
    private final IdentityStatusOutboxService statusOutbox;
    private final Duration resetTtl;
    private final Duration verificationTtl;
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
            IdentityStatusOutboxService statusOutbox,
            @Value("${app.security-token.password-reset-ttl:PT15M}") Duration resetTtl,
            @Value("${app.security-token.email-verification-ttl:PT24H}") Duration verificationTtl,
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
        this.statusOutbox = statusOutbox;
        this.resetTtl = requirePositive(resetTtl, "password reset TTL");
        this.verificationTtl = requirePositive(verificationTtl, "email verification TTL");
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
        boolean becameActive = account.getUserId() != null && Boolean.TRUE.equals(account.getIsActive())
                && account.getLifecycleStatus() != IdentityLifecycleStatus.ACTIVE;
        if (becameActive) {
            account.setLifecycleStatus(IdentityLifecycleStatus.ACTIVE);
            account.setLifecycleVersion((account.getLifecycleVersion() == null ? 0L : account.getLifecycleVersion()) + 1L);
        }
        accounts.save(account);
        token.setConsumedAt(now);
        tokens.save(token);
        tokens.consumeOutstanding(account.getId(), Purpose.EMAIL_VERIFICATION, now);
        if (becameActive) {
            statusOutbox.statusChanged(account, null, "EMAIL_VERIFIED");
        }
        auditService.recordTransactional(
                account.getId(), "EMAIL_VERIFICATION_COMPLETE", "SUCCESS", null, clientIp);
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
