package com.delivery.auth_service.service;

import java.util.Optional;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;

import com.delivery.auth_service.config.UserServiceConfig;
import com.delivery.auth_service.dto.LoginRequest;
import com.delivery.auth_service.dto.RegisterRequest;
import com.delivery.auth_service.dto.RefreshTokenRequest;
import com.delivery.auth_service.dto.UserResponse;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.AuthSession;
import com.delivery.auth_service.entity.RefreshTokenRecord;
import com.delivery.auth_service.exception.InvalidCredentialsException;
import com.delivery.auth_service.exception.InvalidTokenException;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.AuthSessionRepository;
import com.delivery.auth_service.repository.RefreshTokenRecordRepository;
import com.delivery.auth_service.payload.BaseResponse;
import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.SimulationContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;

class AuthServiceSecurityTest {

    private final AuthAccountRepository accountRepository = mock(AuthAccountRepository.class);
    private final AuthSessionRepository sessionRepository = mock(AuthSessionRepository.class);
    private final RefreshTokenRecordRepository refreshTokenRepository = mock(RefreshTokenRecordRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
    private final UserServiceConfig userServiceConfig = userServiceConfig();
    private final AuthService service = new AuthService(
            accountRepository,
            sessionRepository,
            refreshTokenRepository,
            passwordEncoder,
            tokenService,
            userServiceConfig,
            restTemplate,
            googleTokenVerifier);

    @Test
    void publicRegistrationCannotCreateAdminAccount() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("admin@example.com");
        request.setPassword("secret");
        request.setRole("ADMIN");
        when(accountRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be self-registered");
        verify(accountRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publicRegistrationCannotCreateShipperWithoutProfileOnboarding() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("shipper@example.com");
        request.setPassword("secret");
        request.setRole("SHIPPER");

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator provisioning");
        verify(accountRepository, never()).save(any(AuthAccount.class));
        verifyNoInteractions(restTemplate);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void operatorProvisioningCanCreateShipperAccountAndUserProjection() {
        when(accountRepository.findByEmail("shipper@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("hash");
        when(accountRepository.save(any(AuthAccount.class))).thenAnswer(invocation -> {
            AuthAccount account = invocation.getArgument(0);
            if (account.getId() == null) {
                ReflectionTestUtils.setField(account, "id", 43L);
            }
            return account;
        });
        UserResponse user = UserResponse.builder()
                .id(53L).authId(43L).email("shipper@example.com").role("SHIPPER").build();
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(BaseResponse.success(user, "ok")));

        AuthAccount account = service.operatorProvisionShipperAccount(
                "shipper@example.com", "secret");

        assertThat(account.getRole()).isEqualTo(AuthAccount.Role.SHIPPER);
        assertThat(account.getUserId()).isEqualTo(53L);
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                entity.capture(),
                any(ParameterizedTypeReference.class));
        assertThat(entity.getValue().getHeaders().getFirst("Internal-Token"))
                .isEqualTo("service-secret");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void operatorProvisioningCanCreateAdminAccountAndUserProjection() {
        when(accountRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("hash");
        when(accountRepository.save(any(AuthAccount.class))).thenAnswer(invocation -> {
            AuthAccount account = invocation.getArgument(0);
            if (account.getId() == null) {
                ReflectionTestUtils.setField(account, "id", 45L);
            }
            return account;
        });
        UserResponse user = UserResponse.builder()
                .id(55L).authId(45L).email("admin@example.com").role("ADMIN").build();
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(BaseResponse.success(user, "ok")));

        AuthAccount account = service.operatorProvisionAdminAccount(
                "admin@example.com", "secret");

        assertThat(account.getRole()).isEqualTo(AuthAccount.Role.ADMIN);
        assertThat(account.getUserId()).isEqualTo(55L);
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                entity.capture(),
                any(ParameterizedTypeReference.class));
        assertThat(entity.getValue().getHeaders().getFirst("Internal-Token"))
                .isEqualTo("service-secret");
    }

    @Test
    void operatorAdminProvisioningRejectsExistingNonAdminAccount() {
        AuthAccount existing = new AuthAccount();
        existing.setEmail("admin@example.com");
        existing.setPasswordHash("hash");
        existing.setRole(AuthAccount.Role.USER);
        existing.setIsActive(true);
        when(accountRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.operatorProvisionAdminAccount(
                "admin@example.com", "secret"))
                .isInstanceOf(com.delivery.auth_service.exception.EmailAlreadyExistsException.class);

        verifyNoInteractions(restTemplate);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void operatorProvisioningResumesExistingUnlinkedShipperOnlyWithSamePassword() {
        AuthAccount pending = new AuthAccount();
        ReflectionTestUtils.setField(pending, "id", 44L);
        pending.setEmail("shipper@example.com");
        pending.setPasswordHash("hash");
        pending.setRole(AuthAccount.Role.SHIPPER);
        pending.setIsActive(true);
        when(accountRepository.findByEmail("shipper@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        UserResponse user = UserResponse.builder()
                .id(54L).authId(44L).email("shipper@example.com").role("SHIPPER").build();
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(BaseResponse.success(user, "ok")));

        AuthAccount account = service.operatorProvisionShipperAccount(
                "shipper@example.com", "secret");

        assertThat(account).isSameAs(pending);
        assertThat(account.getUserId()).isEqualTo(54L);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void operatorProvisioningRejectsExistingAccountWithDifferentPasswordOrRole() {
        AuthAccount existing = new AuthAccount();
        existing.setEmail("shipper@example.com");
        existing.setPasswordHash("hash");
        existing.setRole(AuthAccount.Role.USER);
        existing.setIsActive(true);
        when(accountRepository.findByEmail("shipper@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.operatorProvisionShipperAccount(
                "shipper@example.com", "secret"))
                .isInstanceOf(com.delivery.auth_service.exception.EmailAlreadyExistsException.class);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void newSocialLoginCannotCreateShipperWithoutProfileOnboarding() {
        var payload = mock(com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload.class);
        when(payload.getEmail()).thenReturn("shipper@example.com");
        when(googleTokenVerifier.verify("signed-google-token")).thenReturn(payload);
        when(accountRepository.findByEmail("shipper@example.com")).thenReturn(Optional.empty());

        var request = com.delivery.auth_service.dto.SocialLoginRequest.builder()
                .provider("google")
                .token("signed-google-token")
                .role("SHIPPER")
                .deviceId("phone-1")
                .build();

        assertThatThrownBy(() -> service.socialLogin(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator provisioning");
        verify(accountRepository, never()).save(any(AuthAccount.class));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void existingProvisionedShipperMayStillUseSocialLogin() {
        var payload = mock(com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload.class);
        when(payload.getEmail()).thenReturn("shipper@example.com");
        when(googleTokenVerifier.verify("signed-google-token")).thenReturn(payload);
        AuthAccount account = new AuthAccount();
        ReflectionTestUtils.setField(account, "id", 51L);
        account.setUserId(61L);
        account.setEmail("shipper@example.com");
        account.setPasswordHash("operator-hash");
        account.setRole(AuthAccount.Role.SHIPPER);
        account.setIsActive(true);
        account.setEmailVerifiedAt(LocalDateTime.now());
        account.setEmailVerificationRequired(false);
        account.setEmailVerificationRequired(true);
        when(accountRepository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(tokenService.generateToken(
                eq(61L), eq(51L), eq(account.getEmail()), eq("SHIPPER"), any(SimulationContext.class)))
                .thenReturn("access");
        when(tokenService.generateRefreshToken(
                eq(61L), eq(51L), eq(account.getEmail()), eq("SHIPPER"), anyString()))
                .thenReturn("refresh");

        var request = com.delivery.auth_service.dto.SocialLoginRequest.builder()
                .provider("google")
                .token("signed-google-token")
                .role("SHIPPER")
                .deviceId("phone-1")
                .deviceType("MOBILE")
                .build();

        var response = service.socialLogin(request);

        assertThat(response.getAuthId()).isEqualTo(51L);
        assertThat(response.getRole()).isEqualTo("SHIPPER");
        assertThat(account.getEmailVerifiedAt()).isNotNull();
        assertThat(account.getEmailVerificationRequired()).isFalse();
        verifyNoInteractions(restTemplate);
        verify(accountRepository).save(account);
        verify(sessionRepository).save(any(AuthSession.class));
    }

    @Test
    void concurrentSocialRegistrationUsesTheWinningAccount() {
        var payload = mock(com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload.class);
        when(payload.getEmail()).thenReturn("social-race@example.com");
        when(googleTokenVerifier.verify("signed-google-token")).thenReturn(payload);
        AuthAccount winner = new AuthAccount();
        ReflectionTestUtils.setField(winner, "id", 71L);
        winner.setUserId(81L);
        winner.setEmail("social-race@example.com");
        winner.setPasswordHash("winner-hash");
        winner.setRole(AuthAccount.Role.USER);
        winner.setIsActive(true);
        winner.setEmailVerifiedAt(LocalDateTime.now());
        winner.setEmailVerificationRequired(false);
        when(accountRepository.findByEmail(winner.getEmail()))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(accountRepository.save(any(AuthAccount.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));
        when(tokenService.generateToken(
                eq(81L), eq(71L), eq(winner.getEmail()), eq("USER"), any(SimulationContext.class)))
                .thenReturn("access");
        when(tokenService.generateRefreshToken(
                eq(81L), eq(71L), eq(winner.getEmail()), eq("USER"), anyString()))
                .thenReturn("refresh");

        var request = com.delivery.auth_service.dto.SocialLoginRequest.builder()
                .provider("google")
                .token("signed-google-token")
                .role("USER")
                .deviceId("phone-1")
                .build();

        var response = service.socialLogin(request);

        assertThat(response.getAuthId()).isEqualTo(71L);
        assertThat(response.getRole()).isEqualTo("USER");
        verifyNoInteractions(restTemplate);
        verify(sessionRepository).save(any(AuthSession.class));
    }

    @Test
    void loginCannotIssueJwtForAccountWithoutLinkedUserProfile() {
        AuthAccount account = new AuthAccount();
        account.setEmail("user@example.com");
        account.setPasswordHash("hash");
        account.setRole(AuthAccount.Role.USER);
        account.setIsActive(true);
        account.setLifecycleStatus(IdentityLifecycleStatus.ACTIVE);
        when(accountRepository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setEmail(account.getEmail());
        request.setPassword("secret");
        request.setDeviceId("device-1");

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("not provisioned");
        verify(tokenService, never()).generateToken(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passwordLoginRejectsNewAccountUntilEmailIsVerified() {
        AuthAccount account = new AuthAccount();
        ReflectionTestUtils.setField(account, "id", 5L);
        account.setUserId(15L);
        account.setEmail("pending-verification@example.com");
        account.setPasswordHash("hash");
        account.setRole(AuthAccount.Role.USER);
        account.setIsActive(true);
        account.setEmailVerificationRequired(true);
        account.setEmailVerifiedAt(null);
        when(accountRepository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setEmail(account.getEmail());
        request.setPassword("secret");
        request.setDeviceId("device-1");

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email verification required");
        verifyNoInteractions(tokenService);
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void loginUsesLinkedProfileIdAsTheTrustedJwtIdentity() {
        AuthAccount account = new AuthAccount();
        ReflectionTestUtils.setField(account, "id", 3L);
        account.setUserId(17L);
        account.setEmail("user@example.com");
        account.setPasswordHash("hash");
        account.setRole(AuthAccount.Role.USER);
        account.setIsActive(true);
        account.setLifecycleStatus(IdentityLifecycleStatus.ACTIVE);
        when(accountRepository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(tokenService.generateToken(
                eq(17L), eq(3L), eq(account.getEmail()), eq("USER"), any(SimulationContext.class)))
                .thenReturn("access-token");
        when(tokenService.generateRefreshToken(
                eq(17L), eq(3L), eq(account.getEmail()), eq("USER"), anyString()))
                .thenReturn("refresh-token");

        LoginRequest request = new LoginRequest();
        request.setEmail(account.getEmail());
        request.setPassword("secret");
        request.setDeviceId("device-1");

        var response = service.login(request);

        assertThat(response.getAuthId()).isEqualTo(3L);
        verify(sessionRepository).deactivateActiveSessionsForDevice(
                eq(3L), eq("device-1"), any(LocalDateTime.class));
        verify(tokenService).generateToken(
                eq(17L), eq(3L), eq(account.getEmail()), eq("USER"), any(SimulationContext.class));
        verify(tokenService).generateRefreshToken(
                eq(17L), eq(3L), eq(account.getEmail()), eq("USER"), anyString());
        verify(tokenService, never()).generateToken(
                eq(3L), eq(3L), eq(account.getEmail()), eq("USER"), any(SimulationContext.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void socialProvisioningFailureRemainsAServiceFailureInsteadOfInvalidCredentials() {
        var payload = mock(com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload.class);
        when(payload.getEmail()).thenReturn("social@example.com");
        when(googleTokenVerifier.verify("signed-google-token")).thenReturn(payload);
        when(accountRepository.findByEmail("social@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(accountRepository.save(any(AuthAccount.class))).thenAnswer(invocation -> {
            AuthAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 9L);
            return account;
        });
        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new ResourceAccessException("user-service unavailable"));

        var request = com.delivery.auth_service.dto.SocialLoginRequest.builder()
                .provider("google")
                .token("signed-google-token")
                .deviceId("phone-1")
                .build();

        assertThatThrownBy(() -> service.socialLogin(request))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("provision");
    }

    @Test
    void refreshRejectsExpiredSessionUnderLock() {
        String refreshToken = "refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);
        AuthSession session = new AuthSession();
        session.setIsActive(true);
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        RefreshTokenRecord token = new RefreshTokenRecord();
        token.setAuthSession(session);
        token.setState(RefreshTokenRecord.State.CURRENT);

        when(tokenService.isValidRefreshToken(refreshToken)).thenReturn(true);
        when(refreshTokenRepository.findByTokenHashForUpdate(AuthService.fingerprint(refreshToken)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refreshToken(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("inactive");
        verify(tokenService, never()).generateRefreshToken(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void logoutRevokesSessionAndItsServerSideExpiry() {
        String refreshToken = "refresh-token";
        AuthSession session = new AuthSession();
        ReflectionTestUtils.setField(session, "id", 71L);
        session.setIsActive(true);
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        RefreshTokenRecord token = new RefreshTokenRecord();
        token.setAuthSession(session);
        token.setState(RefreshTokenRecord.State.CURRENT);
        when(tokenService.isValidRefreshToken(refreshToken)).thenReturn(true);
        when(refreshTokenRepository.findByTokenHashForUpdate(AuthService.fingerprint(refreshToken)))
                .thenReturn(Optional.of(token));

        service.logout(refreshToken);

        verify(refreshTokenRepository).revokeFamily(
                eq(71L), eq(RefreshTokenRecord.State.REVOKED), any(LocalDateTime.class));
        verify(sessionRepository).save(session);
        org.assertj.core.api.Assertions.assertThat(session.getIsActive()).isFalse();
        org.assertj.core.api.Assertions.assertThat(session.getExpiresAt())
                .isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void publicRegistrationCreatesOnlyTheAuthIdentity() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret");
        request.setRole("USER");
        when(accountRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hash");
        when(accountRepository.save(any(AuthAccount.class))).thenAnswer(invocation -> {
            AuthAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 3L);
            return account;
        });
        AuthAccount result = service.register(request);

        assertThat(result.getId()).isEqualTo(3L);
        assertThat(result.getUserId()).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void registrationCanResumeAnUnlinkedAccountWithTheSameCredentials() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("pending@example.com");
        request.setPassword("secret");
        request.setRole("USER");

        AuthAccount pending = new AuthAccount();
        ReflectionTestUtils.setField(pending, "id", 11L);
        pending.setEmail(request.getEmail());
        pending.setPasswordHash("hash");
        pending.setRole(AuthAccount.Role.USER);
        pending.setIsActive(true);

        when(accountRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches(request.getPassword(), pending.getPasswordHash())).thenReturn(true);
        AuthAccount result = service.register(request);

        assertThat(result).isSameAs(pending);
        assertThat(result.getUserId()).isNull();
        verify(passwordEncoder, never()).encode(anyString());
        verify(accountRepository, never()).save(pending);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void authRegistrationDoesNotDependOnUserServiceAvailability() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("retry@example.com");
        request.setPassword("secret");
        request.setRole("USER");
        when(accountRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hash");
        when(accountRepository.save(any(AuthAccount.class))).thenAnswer(invocation -> {
            AuthAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 23L);
            return account;
        });
        AuthAccount result = service.register(request);

        assertThat(result.getId()).isEqualTo(23L);
        assertThat(result.getUserId()).isNull();
        verify(accountRepository, times(1)).save(any(AuthAccount.class));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void concurrentPasswordRegistrationResumesTheWinningUnlinkedAccount() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("race@example.com");
        request.setPassword("secret");
        request.setRole("USER");

        AuthAccount winner = new AuthAccount();
        ReflectionTestUtils.setField(winner, "id", 31L);
        winner.setEmail(request.getEmail());
        winner.setPasswordHash("winner-hash");
        winner.setRole(AuthAccount.Role.USER);
        winner.setIsActive(true);

        when(accountRepository.findByEmail(request.getEmail()))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("loser-hash");
        when(passwordEncoder.matches(request.getPassword(), winner.getPasswordHash())).thenReturn(true);
        when(accountRepository.save(any(AuthAccount.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AuthAccount result = service.register(request);

        assertThat(result).isSameAs(winner);
        assertThat(result.getUserId()).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void pendingRegistrationCannotBeClaimedWithDifferentCredentials() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("pending@example.com");
        request.setPassword("attacker-password");
        request.setRole("USER");

        AuthAccount pending = new AuthAccount();
        pending.setEmail(request.getEmail());
        pending.setPasswordHash("original-hash");
        pending.setRole(AuthAccount.Role.USER);
        pending.setIsActive(true);
        when(accountRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches(request.getPassword(), pending.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(com.delivery.auth_service.exception.EmailAlreadyExistsException.class);

        verify(restTemplate, never()).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    void activeSessionListFiltersExpiredRowsAndCapsRepositoryQuery() {
        AuthAccount account = new AuthAccount();
        account.setEmail("user@example.com");
        when(accountRepository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(sessionRepository.findActiveUnexpiredByAuthAccount(
                eq(account), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(java.util.List.of());

        assertThat(service.getActiveSessions(account.getEmail())).isEmpty();

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sessionRepository).findActiveUnexpiredByAuthAccount(
                eq(account), now.capture(), pageable.capture());
        assertThat(now.getValue()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void blockMarksUserProfileSyncPendingAndDefersRemoteCallUntilCommit() {
        AuthAccount account = new AuthAccount();
        ReflectionTestUtils.setField(account, "id", 3L);
        account.setUserId(7L);
        account.setIsActive(true);
        when(accountRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(account));
        when(accountRepository.findById(3L)).thenReturn(Optional.of(account));
        when(restTemplate.exchange(
                eq("http://user-service:8082/api/internal/users/7/block-status"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(BaseResponse.success(null, "ok")));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.blockAccount(3L, 1L, "fraud review");

            assertThat(account.getIsActive()).isFalse();
            assertThat(account.getUserStatusSyncPending()).isTrue();
            assertThat(account.getUserStatusSyncVersion()).isEqualTo(1L);
            assertThat(account.getUserStatusSyncAdminId()).isEqualTo(1L);
            assertThat(account.getUserStatusSyncBlockReason()).isEqualTo("fraud review");
            verifyNoInteractions(restTemplate);

            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(accountRepository).findByIdForUpdate(3L);
        verify(sessionRepository).deactivateAllActiveSessions(eq(3L), any(LocalDateTime.class));
        verify(accountRepository).clearUserStatusSyncPending(
                eq(3L), eq(1L), any(LocalDateTime.class));
    }

    @Test
    void blockAccountWithoutLinkedUserDoesNotCreateProjectionSyncWork() {
        AuthAccount account = new AuthAccount();
        ReflectionTestUtils.setField(account, "id", 3L);
        account.setUserId(null);
        account.setIsActive(true);
        when(accountRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(account));

        service.blockAccount(3L, 1L, "fraud review");

        assertThat(account.getIsActive()).isFalse();
        assertThat(account.getUserStatusSyncPending()).isFalse();
        assertThat(account.getUserStatusSyncVersion()).isEqualTo(0L);
        verify(accountRepository).findByIdForUpdate(3L);
        verify(accountRepository).save(account);
        verify(sessionRepository).deactivateAllActiveSessions(eq(3L), any(LocalDateTime.class));
        verifyNoInteractions(restTemplate);
        verify(accountRepository, never()).findById(3L);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void unblockSynchronizesUserProfileWithInternalCredentialAfterCommit() {
        AuthAccount account = new AuthAccount();
        ReflectionTestUtils.setField(account, "id", 3L);
        account.setUserId(7L);
        account.setIsActive(false);
        when(accountRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(account));
        when(accountRepository.findById(3L)).thenReturn(Optional.of(account));
        when(restTemplate.exchange(
                eq("http://user-service:8082/api/internal/users/7/block-status"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(BaseResponse.success(null, "ok")));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.unblockAccount(3L, 1L);

            assertThat(account.getIsActive()).isTrue();
            assertThat(account.getUserStatusSyncPending()).isTrue();
            assertThat(account.getUserStatusSyncVersion()).isEqualTo(1L);
            assertThat(account.getUserStatusSyncBlockReason()).isNull();
            verifyNoInteractions(restTemplate);

            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(accountRepository).findByIdForUpdate(3L);
        ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://user-service:8082/api/internal/users/7/block-status"),
                eq(HttpMethod.POST),
                request.capture(),
                any(ParameterizedTypeReference.class));
        assertThat(request.getValue().getHeaders().getFirst("Internal-Token"))
                .isEqualTo("service-secret");
        java.util.Map<String, Object> requestBody = (java.util.Map<String, Object>) request.getValue().getBody();
        assertThat(requestBody)
                .containsEntry("adminId", 1L)
                .containsEntry("blocked", false);
        verify(accountRepository).clearUserStatusSyncPending(
                eq(3L), eq(1L), any(LocalDateTime.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void afterCommitSyncFailureIsReportedAndLeftPendingForRetry() {
        AuthAccount account = new AuthAccount();
        ReflectionTestUtils.setField(account, "id", 3L);
        account.setUserId(7L);
        account.setIsActive(true);
        when(accountRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(account));
        when(accountRepository.findById(3L)).thenReturn(Optional.of(account));
        when(restTemplate.exchange(
                eq("http://user-service:8082/api/internal/users/7/block-status"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new ResourceAccessException("user-service unavailable"));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.blockAccount(3L, 1L, "fraud review");

            assertThatThrownBy(() -> TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("synchronize");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(account.getUserStatusSyncPending()).isTrue();
        verify(accountRepository).recordUserStatusSyncFailure(
                eq(3L), eq(1L), anyString(), any(LocalDateTime.class));
        verify(accountRepository, never()).clearUserStatusSyncPending(
                eq(3L), eq(1L), any(LocalDateTime.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void reconciliationRecordsUserProfileSynchronizationFailureAndContinuesBatch() {
        AuthAccount failed = new AuthAccount();
        ReflectionTestUtils.setField(failed, "id", 3L);
        failed.setUserId(7L);
        failed.setIsActive(false);
        failed.setUserStatusSyncPending(true);
        failed.setUserStatusSyncVersion(4L);
        failed.setUserStatusSyncAdminId(1L);
        failed.setUserStatusSyncBlockReason("fraud review");

        AuthAccount next = new AuthAccount();
        ReflectionTestUtils.setField(next, "id", 4L);
        next.setUserId(8L);
        next.setIsActive(true);
        next.setUserStatusSyncPending(true);
        next.setUserStatusSyncVersion(5L);
        next.setUserStatusSyncAdminId(1L);

        when(accountRepository.findPendingUserStatusSync(any(Pageable.class)))
                .thenReturn(java.util.List.of(failed, next));
        when(restTemplate.exchange(
                eq("http://user-service:8082/api/internal/users/7/block-status"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new ResourceAccessException("user-service unavailable"));
        when(restTemplate.exchange(
                eq("http://user-service:8082/api/internal/users/8/block-status"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(BaseResponse.success(null, "ok")));

        service.reconcilePendingUserStatusSync();

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(accountRepository).recordUserStatusSyncFailure(
                eq(3L), eq(4L), error.capture(), any(LocalDateTime.class));
        assertThat(error.getValue()).contains("Failed to synchronize");
        verify(accountRepository, never()).clearUserStatusSyncPending(
                eq(3L), eq(4L), any(LocalDateTime.class));
        verify(accountRepository).clearUserStatusSyncPending(
                eq(4L), eq(5L), any(LocalDateTime.class));
    }

    private static UserServiceConfig userServiceConfig() {
        UserServiceConfig config = new UserServiceConfig();
        config.setUrl("http://user-service:8082");
        config.setInternalSecret("service-secret");
        return config;
    }
}
