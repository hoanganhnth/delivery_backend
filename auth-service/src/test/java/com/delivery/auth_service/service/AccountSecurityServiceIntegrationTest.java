package com.delivery.auth_service.service;

import com.delivery.auth_service.TestJwtKeyProperties;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.AuthSecurityAudit;
import com.delivery.auth_service.entity.AuthSecurityToken;
import com.delivery.auth_service.entity.AuthSession;
import com.delivery.auth_service.entity.RefreshTokenRecord;
import com.delivery.auth_service.exception.InvalidTokenException;
import com.delivery.auth_service.exception.ProvisioningConflictException;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.AuthSecurityAuditRepository;
import com.delivery.auth_service.repository.AuthSecurityTokenRepository;
import com.delivery.auth_service.repository.AuthSessionRepository;
import com.delivery.auth_service.repository.RefreshTokenRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_security_flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "app.security-token.password-reset-ttl=PT15M",
        "app.security-token.email-verification-ttl=PT24H",
        "app.security-token.user-provisioning-ttl=PT15M"
})
class AccountSecurityServiceIntegrationTest {
    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) {
        TestJwtKeyProperties.register(registry);
    }

    @Autowired AccountSecurityService security;
    @Autowired AuthAccountRepository accounts;
    @Autowired AuthSecurityTokenRepository tokens;
    @Autowired AuthSecurityAuditRepository audits;
    @Autowired AuthSessionRepository sessions;
    @Autowired RefreshTokenRecordRepository refreshTokens;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean SecurityEmailSender emailSender;

    @BeforeEach
    void clean() {
        audits.deleteAll();
        tokens.deleteAll();
        refreshTokens.deleteAll();
        sessions.deleteAll();
        accounts.deleteAll();
        clearInvocations(emailSender);
    }

    @Test
    void existingAndMissingEmailHaveTheSameAcceptedServiceContract() {
        account("known@example.com", false);

        security.requestPasswordReset("known@example.com", "127.0.0.1");
        security.requestPasswordReset("missing@example.com", "127.0.0.1");

        verify(emailSender, org.mockito.Mockito.timeout(3000))
                .sendPasswordReset(org.mockito.ArgumentMatchers.eq("known@example.com"), anyString());
        verify(emailSender, never()).sendPasswordReset(
                org.mockito.ArgumentMatchers.eq("missing@example.com"), anyString());
        assertThat(audits.findAll()).hasSizeGreaterThanOrEqualTo(2).allSatisfy(audit -> {
            assertThat(audit.getOutcome()).isIn("QUEUED", "DELIVERED", "ACCEPTED");
            assertThat(audit.getSubjectHash()).hasSize(64);
        });
    }

    @Test
    void expiredAndReusedResetTokensFailClosed() {
        AuthAccount account = account("reset@example.com", false);
        security.requestPasswordReset(account.getEmail(), "127.0.0.1");
        String raw = capturedResetToken();
        AuthSecurityToken stored = tokens.findAll().get(0);
        stored.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        tokens.saveAndFlush(stored);

        assertThatThrownBy(() -> security.resetPassword(raw, "NewPassword1!", "127.0.0.1"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid or expired security token");

        security.requestPasswordReset(account.getEmail(), "127.0.0.1");
        String replacement = latestCapturedResetToken();
        security.resetPassword(replacement, "NewPassword2!", "127.0.0.1");
        assertThatThrownBy(() -> security.resetPassword(replacement, "NewPassword3!", "127.0.0.1"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid or expired security token");
    }

    @Test
    void wrongPurposeCannotResetPasswordAndVerificationBindsToOwningAccount() {
        AuthAccount owner = account("owner@example.com", true);
        AuthAccount other = account("other@example.com", true);
        security.requestEmailVerification(owner.getEmail(), "127.0.0.1");
        String verificationToken = capturedVerificationToken();

        assertThatThrownBy(() -> security.resetPassword(
                verificationToken, "NewPassword1!", "127.0.0.1"))
                .isInstanceOf(InvalidTokenException.class);

        security.verifyEmail(verificationToken, "127.0.0.1");

        AuthAccount verified = accounts.findById(owner.getId()).orElseThrow();
        AuthAccount untouched = accounts.findById(other.getId()).orElseThrow();
        assertThat(verified.getEmailVerifiedAt()).isNotNull();
        assertThat(verified.getEmailVerificationRequired()).isFalse();
        assertThat(untouched.getEmailVerifiedAt()).isNull();
        assertThat(untouched.getEmailVerificationRequired()).isTrue();
    }

    @Test
    void passwordChangeRevokesSessionAndRefreshFamilyAtomically() {
        AuthAccount account = account("session@example.com", false);
        AuthSession session = new AuthSession();
        session.setAuthAccount(account);
        session.setDeviceId("phone-1");
        session.setTokenFamilyId("11111111-1111-1111-1111-111111111111");
        session.setIsActive(true);
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        session = sessions.saveAndFlush(session);

        RefreshTokenRecord refresh = new RefreshTokenRecord();
        refresh.setAuthSession(session);
        refresh.setTokenHash("a".repeat(64));
        refresh.setState(RefreshTokenRecord.State.CURRENT);
        refresh.setIssuedAt(LocalDateTime.now());
        refresh.setExpiresAt(LocalDateTime.now().plusDays(1));
        refreshTokens.saveAndFlush(refresh);

        security.requestPasswordReset(account.getEmail(), "127.0.0.1");
        security.resetPassword(capturedResetToken(), "ChangedPassword1!", "127.0.0.1");

        AuthAccount changed = accounts.findById(account.getId()).orElseThrow();
        AuthSession revokedSession = sessions.findById(session.getId()).orElseThrow();
        RefreshTokenRecord revokedRefresh = refreshTokens.findById(refresh.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("ChangedPassword1!", changed.getPasswordHash())).isTrue();
        assertThat(revokedSession.getIsActive()).isFalse();
        assertThat(revokedSession.getExpiresAt()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(revokedRefresh.getState()).isEqualTo(RefreshTokenRecord.State.REVOKED);
        assertThat(revokedRefresh.getRevokedAt()).isNotNull();
    }

    @Test
    void rawSecurityTokensNeverAppearInPersistenceOrAudit() {
        AuthAccount account = account("privacy@example.com", false);
        security.requestPasswordReset(account.getEmail(), "127.0.0.1");
        String raw = capturedResetToken();

        assertThat(tokens.findAll()).singleElement().satisfies(token -> {
            assertThat(token.getTokenHash()).hasSize(64).isNotEqualTo(raw);
        });
        List<AuthSecurityAudit> persistedAudits = audits.findAll();
        assertThat(persistedAudits).allSatisfy(audit -> {
            assertThat(audit.getAction()).doesNotContain(raw);
            assertThat(audit.getOutcome()).doesNotContain(raw);
            assertThat(audit.getSubjectHash()).doesNotContain(raw);
            assertThat(audit.getClientIpHash()).doesNotContain(raw);
        });
    }

    @Test
    void userProvisioningHandoffLinksExactlyOneUserAndIsRetryable() {
        AuthAccount account = unlinkedAccount("registration@example.com");

        String raw = security.issueUserProvisioning(account, "127.0.0.1");
        var identity = security.resolveUserProvisioning(raw);

        assertThat(identity.getAuthId()).isEqualTo(account.getId());
        assertThat(identity.getEmail()).isEqualTo(account.getEmail());
        assertThat(identity.getRole()).isEqualTo("USER");
        assertThat(tokens.findAll()).singleElement().satisfies(token -> {
            assertThat(token.getPurpose()).isEqualTo(AuthSecurityToken.Purpose.USER_PROVISIONING);
            assertThat(token.getTokenHash()).hasSize(64).isNotEqualTo(raw);
            assertThat(token.getConsumedAt()).isNull();
        });

        security.completeUserProvisioning(raw, 77L);
        security.resolveUserProvisioning(raw);
        security.completeUserProvisioning(raw, 77L);

        assertThat(accounts.findById(account.getId()).orElseThrow().getUserId()).isEqualTo(77L);
        assertThat(tokens.findAll()).singleElement()
                .satisfies(token -> assertThat(token.getConsumedAt()).isNotNull());
        assertThatThrownBy(() -> security.completeUserProvisioning(raw, 78L))
                .isInstanceOf(ProvisioningConflictException.class);
    }

    @Test
    void freshUserProvisioningHandoffInvalidatesThePreviousOne() {
        AuthAccount account = unlinkedAccount("retry-registration@example.com");
        String first = security.issueUserProvisioning(account, "127.0.0.1");
        String replacement = security.issueUserProvisioning(account, "127.0.0.1");

        assertThatThrownBy(() -> security.resolveUserProvisioning(first))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(security.resolveUserProvisioning(replacement).getAuthId())
                .isEqualTo(account.getId());
    }

    private AuthAccount account(String email, boolean requiresVerification) {
        AuthAccount account = new AuthAccount();
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode("OriginalPassword1!"));
        account.setRole(AuthAccount.Role.USER);
        account.setIsActive(true);
        account.setUserId(Math.abs((long) email.hashCode()) + 1);
        account.setEmailVerificationRequired(requiresVerification);
        account.setEmailVerifiedAt(requiresVerification ? null : LocalDateTime.now());
        return accounts.saveAndFlush(account);
    }

    private AuthAccount unlinkedAccount(String email) {
        AuthAccount account = new AuthAccount();
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode("OriginalPassword1!"));
        account.setRole(AuthAccount.Role.USER);
        account.setIsActive(true);
        account.setUserId(null);
        account.setEmailVerificationRequired(true);
        account.setEmailVerifiedAt(null);
        return accounts.saveAndFlush(account);
    }

    private String capturedResetToken() {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender, org.mockito.Mockito.timeout(3000))
                .sendPasswordReset(anyString(), captor.capture());
        return captor.getValue();
    }

    private String latestCapturedResetToken() {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender, org.mockito.Mockito.timeout(3000).atLeast(2))
                .sendPasswordReset(anyString(), captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    private String capturedVerificationToken() {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender, org.mockito.Mockito.timeout(3000))
                .sendEmailVerification(anyString(), captor.capture());
        return captor.getValue();
    }
}
