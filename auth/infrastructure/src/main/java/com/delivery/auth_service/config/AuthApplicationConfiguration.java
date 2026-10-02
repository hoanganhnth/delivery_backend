package com.delivery.auth_service.config;

import com.delivery.auth.application.DefaultRegistrationUseCase;
import com.delivery.auth.application.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthApplicationConfiguration {
    @Bean SecurityTokenUseCase securityTokenUseCase(AuthTransactionPort transactions, AuthAccountPort accounts,
            SecurityTokenPort tokens, PasswordCredentialPort passwords, SecurityAuditPort audits,
            SecurityEmailPort email, AccountLifecyclePort lifecycle,
            @org.springframework.beans.factory.annotation.Value("${app.security-token.password-reset-ttl:PT15M}") java.time.Duration resetTtl,
            @org.springframework.beans.factory.annotation.Value("${app.security-token.email-verification-ttl:PT24H}") java.time.Duration verificationTtl,
            @org.springframework.beans.factory.annotation.Value("${app.security-token.retention-days:30}") int tokenDays,
            @org.springframework.beans.factory.annotation.Value("${app.security-audit.retention-days:180}") int auditDays) {
        return new com.delivery.auth.application.DefaultSecurityTokenUseCase(transactions,accounts,tokens,passwords,
                audits,email,lifecycle,resetTtl,verificationTtl,tokenDays,auditDays);
    }

    @Bean SimulationBindingUseCase simulationBindingUseCase(AuthAccountLockPort accounts,
            AuthTransactionPort transactions, SimulationAccessTokenPort tokens) {
        return new com.delivery.auth.application.DefaultSimulationBindingUseCase(accounts, transactions, tokens);
    }

    @Bean AccountLookupUseCase accountLookupUseCase(AuthAccountPort accounts) {
        return new com.delivery.auth.application.DefaultAccountLookupUseCase(accounts);
    }

    @Bean IdentityProfileUseCase identityProfileUseCase(AuthTransactionPort transactions,
            AccountLifecyclePort accounts, IdentityInboxPort receipts) {
        return new com.delivery.auth.application.DefaultIdentityProfileUseCase(transactions, accounts, receipts);
    }

    @Bean AccountLifecycleUseCase accountLifecycleUseCase(AuthAccountPort accounts, AccountLifecyclePort persistence,
            AuthTransactionPort transactions, LifecycleTransactionPort callbacks, UserStatusProjectionPort projection,
            LifecycleTelemetryPort telemetry,
            @org.springframework.beans.factory.annotation.Value("${app.identity.events.enabled:false}") boolean enabled) {
        return new com.delivery.auth.application.DefaultAccountLifecycleUseCase(accounts, persistence, transactions,
                callbacks, projection, telemetry, enabled);
    }

    @Bean FirebaseChatTokenUseCase firebaseChatTokenUseCase(FirebaseChatTokenPort issuer,
            @org.springframework.beans.factory.annotation.Value("${app.firebase.chat.enabled:false}") boolean enabled) {
        return new com.delivery.auth.application.DefaultFirebaseChatTokenUseCase(issuer, enabled);
    }

    @Bean SocialLoginUseCase socialLoginUseCase(AuthTransactionPort transactions, SocialIdentityPort identities,
            AuthAccountPort accounts, PasswordCredentialPort passwords, UserProfileProvisioningUseCase profiles,
            SessionPort sessions, SessionTokenPort tokens, RefreshCredentialPort refresh) {
        return new com.delivery.auth.application.DefaultSocialLoginUseCase(transactions, identities, accounts,
                passwords, profiles, sessions, tokens, refresh);
    }

    @Bean OperatorProvisioningUseCase operatorProvisioningUseCase(AuthAccountPort accounts,
            PasswordCredentialPort credentials, UserProfileProvisioningUseCase profiles) {
        return new com.delivery.auth.application.DefaultOperatorProvisioningUseCase(accounts, credentials, profiles);
    }

    @Bean UserProfileProvisioningUseCase userProfileProvisioningUseCase(UserProfileProvisioningPort profiles) {
        return new com.delivery.auth.application.DefaultUserProfileProvisioningUseCase(profiles);
    }

    @Bean RegistrationAdmissionUseCase registrationAdmissionUseCase(
            @org.springframework.beans.factory.annotation.Value("${app.identity.public-registration-enabled:false}") boolean enabled,
            @org.springframework.beans.factory.annotation.Value("${app.identity.registration.canary-percentage:0}") int percentage,
            @org.springframework.beans.factory.annotation.Value("${app.identity.registration.canary-allowlist:}") String allowlist,
            @org.springframework.beans.factory.annotation.Value("${app.identity.registration.canary-hash-key:}") String hashKey,
            RegistrationCohortPort cohort, RegistrationAdmissionTelemetryPort telemetry) {
        return new com.delivery.auth.application.DefaultRegistrationAdmissionUseCase(enabled, percentage, allowlist,
                hashKey != null && !hashKey.isBlank(), cohort, telemetry);
    }

    @Bean RegistrationRecoveryUseCase registrationRecoveryUseCase(RegistrationRecoveryPort handles,
            @org.springframework.beans.factory.annotation.Value("${app.identity.registration.handle-retention-days:1}") int retentionDays) {
        return new com.delivery.auth.application.DefaultRegistrationRecoveryUseCase(handles, retentionDays, java.time.Clock.systemDefaultZone());
    }

    @Bean DeviceSessionUseCase deviceSessionUseCase(AuthAccountPort accounts, SessionPort sessions,
            SessionCredentialRevocationPort credentials, AuthTransactionPort transactions) {
        return new com.delivery.auth.application.DefaultDeviceSessionUseCase(accounts, sessions, credentials, transactions);
    }

    @Bean com.delivery.auth.application.DefaultRefreshTokenUseCase refreshTokenUseCase(AuthTransactionPort transactions,
            RefreshTokenVerificationPort verification, RefreshCredentialLockPort credentials, SessionTokenPort tokens) {
        return new com.delivery.auth.application.DefaultRefreshTokenUseCase(transactions, verification, credentials, tokens);
    }

    @Bean LoginUseCase loginUseCase(AuthAccountPort accounts, PasswordCredentialPort credentials,
            SessionPort sessions, SessionTokenPort tokens, AuthTransactionPort transactions, RefreshCredentialPort refresh) {
        return new com.delivery.auth.application.DefaultLoginUseCase(accounts, credentials, sessions, tokens, transactions, refresh);
    }

    @Bean RegistrationUseCase registrationUseCase(AuthAccountPort accounts, PasswordCredentialPort credentials,
            ProvisioningTokenPort tokens, RegistrationHandlePort handles) {
        return new DefaultRegistrationUseCase(accounts, credentials, tokens, handles);
    }
}
