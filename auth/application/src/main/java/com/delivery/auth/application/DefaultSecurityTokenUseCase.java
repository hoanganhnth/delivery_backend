package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.SecurityTokenPurpose;
import com.delivery.auth.domain.policy.InvalidAuthToken;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

public final class DefaultSecurityTokenUseCase implements SecurityTokenUseCase {
    private final AuthTransactionPort transactions;
    private final AuthAccountPort accounts;
    private final SecurityTokenPort tokens;
    private final PasswordCredentialPort passwords;
    private final SecurityAuditPort audits;
    private final SecurityEmailPort email;
    private final AccountLifecyclePort lifecycle;
    private final Duration resetTtl, verificationTtl;
    private final int tokenRetentionDays, auditRetentionDays;
    public DefaultSecurityTokenUseCase(AuthTransactionPort transactions, AuthAccountPort accounts,
            SecurityTokenPort tokens, PasswordCredentialPort passwords, SecurityAuditPort audits,
            SecurityEmailPort email, AccountLifecyclePort lifecycle, Duration resetTtl,
            Duration verificationTtl, int tokenRetentionDays, int auditRetentionDays) {
        this.transactions=transactions;this.accounts=accounts;this.tokens=tokens;this.passwords=passwords;
        this.audits=audits;this.email=email;this.lifecycle=lifecycle;
        this.resetTtl=requirePositive(resetTtl,"password reset TTL");
        this.verificationTtl=requirePositive(verificationTtl,"email verification TTL");
        this.tokenRetentionDays=Math.max(1,tokenRetentionDays);
        this.auditRetentionDays=Math.max(30,auditRetentionDays);
    }
    @Override public void requestPasswordReset(String emailAddress,String clientIp) {
        transactions.required(() -> {
            String email=normalizeEmail(emailAddress);
            AuthAccount account=accounts.findByEmail(email).orElse(null);
            if (account==null || !Boolean.TRUE.equals(account.isActive())) {
                audits.recordTransactional(null,"PASSWORD_RESET_REQUEST","ACCEPTED",email,clientIp);
            } else issue(account,SecurityTokenPurpose.PASSWORD_RESET,resetTtl,clientIp);
            return null;
        });
    }
    @Override public void requestEmailVerification(String emailAddress,String clientIp) {
        transactions.required(() -> {
            String email=normalizeEmail(emailAddress);
            AuthAccount account=accounts.findByEmail(email).orElse(null);
            if (account==null || !Boolean.TRUE.equals(account.isActive()) || account.emailVerifiedAt()!=null) {
                audits.recordTransactional(account==null?null:account.id(),"EMAIL_VERIFICATION_REQUEST","ACCEPTED",email,clientIp);
            } else issue(account,SecurityTokenPurpose.EMAIL_VERIFICATION,verificationTtl,clientIp);
            return null;
        });
    }
    private void issue(AuthAccount account,SecurityTokenPurpose purpose,Duration ttl,String clientIp) {
        LocalDateTime now=LocalDateTime.now();
        tokens.consumeOutstanding(account.id(),purpose,now);
        String raw=tokens.randomRawToken();
        tokens.issue(account.id(),purpose,raw,now.plus(ttl));
        String action=purpose==SecurityTokenPurpose.PASSWORD_RESET?"PASSWORD_RESET_REQUEST":"EMAIL_VERIFICATION_REQUEST";
        email.publish(account.id(),account.email(),purpose,raw,clientIp);
        audits.recordTransactional(account.id(),action,"QUEUED",account.email(),clientIp);
    }
    @Override public void resetPassword(String raw,String newPassword,String clientIp) {
        transactions.required(() -> {
            SecurityTokenPort.Token token=usable(raw,SecurityTokenPurpose.PASSWORD_RESET,clientIp);
            LocalDateTime now=LocalDateTime.now();
            accounts.save(accounts.findById(token.accountId()).orElseThrow().withPasswordHash(passwords.hash(newPassword)));
            tokens.consume(token.id(),now);
            tokens.consumeOutstanding(token.accountId(),SecurityTokenPurpose.PASSWORD_RESET,now);
            tokens.revokeCredentials(token.accountId(),now);
            audits.recordTransactional(token.accountId(),"PASSWORD_RESET_COMPLETE","SUCCESS",null,clientIp);
            return null;
        });
    }
    @Override public void verifyEmail(String raw,String clientIp) {
        transactions.required(() -> {
            SecurityTokenPort.Token token=usable(raw,SecurityTokenPurpose.EMAIL_VERIFICATION,clientIp);
            LocalDateTime now=LocalDateTime.now();
            AuthAccount account=accounts.findById(token.accountId()).orElseThrow().withVerifiedEmail(now);
            boolean becameActive=account.userId()!=null && Boolean.TRUE.equals(account.isActive())
                    && account.lifecycleStatus()!=AuthAccount.LifecycleStatus.ACTIVE;
            if (becameActive) account=account.withLifecycleStatus(AuthAccount.LifecycleStatus.ACTIVE);
            account=accounts.save(account);
            tokens.consume(token.id(),now);
            tokens.consumeOutstanding(account.id(),SecurityTokenPurpose.EMAIL_VERIFICATION,now);
            if (becameActive) lifecycle.statusChanged(account,null,"EMAIL_VERIFIED");
            audits.recordTransactional(account.id(),"EMAIL_VERIFICATION_COMPLETE","SUCCESS",null,clientIp);
            return null;
        });
    }
    private SecurityTokenPort.Token usable(String raw,SecurityTokenPurpose expected,String clientIp) {
        if (raw==null || raw.isBlank()) return reject(expected,"INVALID",clientIp);
        var token=tokens.findForUpdate(raw).orElse(null);
        if (token==null) return reject(expected,"INVALID",clientIp);
        if (token.purpose()!=expected) return reject(expected,"WRONG_PURPOSE",clientIp);
        if (token.consumedAt()!=null) return reject(expected,"REUSED",clientIp);
        if (token.expiresAt()==null || !token.expiresAt().isAfter(LocalDateTime.now()))
            return reject(expected,"EXPIRED",clientIp);
        if (!Boolean.TRUE.equals(token.accountActive())) return reject(expected,"INACTIVE_ACCOUNT",clientIp);
        return token;
    }
    private SecurityTokenPort.Token reject(SecurityTokenPurpose purpose,String outcome,String clientIp) {
        audits.recordRejection(purpose.name()+"_CONSUME",outcome,clientIp);
        throw new InvalidAuthToken("Invalid or expired security token");
    }
    @Override public void cleanup() {
        transactions.required(() -> {
            LocalDateTime now=LocalDateTime.now();
            tokens.deleteExpiredBefore(now.minusDays(tokenRetentionDays));
            audits.deleteOlderThan(now.minusDays(auditRetentionDays));
            return null;
        });
    }
    private static String normalizeEmail(String email) {
        return email==null || email.isBlank()?"":email.trim().toLowerCase(Locale.ROOT);
    }
    private static Duration requirePositive(Duration value,String label) {
        if (value==null || value.isZero() || value.isNegative())
            throw new IllegalArgumentException(label+" must be positive");
        return value;
    }
}
