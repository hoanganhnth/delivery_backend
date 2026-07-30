package com.delivery.auth_service.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.delivery.auth_service.config.UserServiceConfig;
import com.delivery.auth_service.config.AuthUserCircuitBreaker;
import com.delivery.auth_service.dto.AuthAccountDto;
import com.delivery.auth_service.dto.AuthResponse;
import com.delivery.auth_service.dto.CreateUserRequest;
import com.delivery.auth_service.dto.LoginRequest;
import com.delivery.auth_service.dto.RefreshTokenRequest;
import com.delivery.auth_service.dto.RegisterRequest;
import com.delivery.auth_service.dto.SessionInfoResponse;
import com.delivery.auth_service.dto.SocialLoginRequest;
import com.delivery.auth_service.dto.UserResponse;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.AuthSession;
import com.delivery.auth_service.exception.EmailAlreadyExistsException;
import com.delivery.auth_service.exception.InvalidCredentialsException;
import com.delivery.auth_service.exception.InvalidTokenException;
import com.delivery.auth_service.exception.ResourceNotFoundException;
import com.delivery.auth_service.payload.BaseResponse;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.AuthSessionRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuthService implements UserDetailsService {

    private static final int USER_STATUS_SYNC_BATCH_SIZE = 50;
    private static final int USER_STATUS_SYNC_ERROR_MAX_LENGTH = 500;

    private final AuthAccountRepository authAccountRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserServiceConfig userServiceConfig;
    private final RestTemplate restTemplate;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final PlatformTransactionManager transactionManager;
    private final AuthUserCircuitBreaker userCircuitBreaker;

    public AuthService(
            AuthAccountRepository authAccountRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            UserServiceConfig userServiceConfig,
            RestTemplate restTemplate,
            GoogleTokenVerifier googleTokenVerifier) {
        this(
                authAccountRepository,
                authSessionRepository,
                passwordEncoder,
                tokenService,
                userServiceConfig,
                restTemplate,
                googleTokenVerifier,
                null,
                null);
    }

    public AuthService(
            AuthAccountRepository authAccountRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            UserServiceConfig userServiceConfig,
            RestTemplate restTemplate,
            GoogleTokenVerifier googleTokenVerifier,
            PlatformTransactionManager transactionManager) {
        this(
                authAccountRepository,
                authSessionRepository,
                passwordEncoder,
                tokenService,
                userServiceConfig,
                restTemplate,
                googleTokenVerifier,
                transactionManager,
                null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            AuthAccountRepository authAccountRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            UserServiceConfig userServiceConfig,
            RestTemplate restTemplate,
            GoogleTokenVerifier googleTokenVerifier,
            PlatformTransactionManager transactionManager,
            AuthUserCircuitBreaker userCircuitBreaker) {
        this.authAccountRepository = authAccountRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userServiceConfig = userServiceConfig;
        this.restTemplate = restTemplate;
        this.googleTokenVerifier = googleTokenVerifier;
        this.transactionManager = transactionManager;
        this.userCircuitBreaker = userCircuitBreaker;
    }

    /**
     * Đăng ký tài khoản mới
     */
    public AuthAccount register(RegisterRequest request) {
        if (request.getRole() == null || request.getRole().isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }

        AuthAccount.Role roleEnum = parsePublicRegistrationRole(request.getRole());

        AuthAccount account = authAccountRepository.findByEmail(request.getEmail())
                .map(existing -> resumePendingPasswordRegistration(existing, request, roleEnum))
                .orElseGet(() -> createPasswordAccountOrResumeRace(request, roleEnum));

        provisionUserProfile(account);

        return account;
    }

    /**
     * Local/operator provisioning path for SHIPPER accounts. This intentionally
     * does not back public registration: SHIPPER still requires an operator-owned
     * fixture/onboarding flow before the shipper profile can be created.
     */
    public AuthAccount operatorProvisionShipperAccount(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Operator-provisioned email is required");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Operator-provisioned password is required");
        }

        AuthAccount account = authAccountRepository.findByEmail(email)
                .map(existing -> resumeOperatorPasswordAccount(
                        existing, email, password, AuthAccount.Role.SHIPPER))
                .orElseGet(() -> createOperatorPasswordAccount(email, password, AuthAccount.Role.SHIPPER));

        if (account.getUserId() == null) {
            provisionUserProfile(account);
        }

        return account;
    }

    /**
     * Local/operator provisioning path for ADMIN accounts. This is intentionally
     * isolated from public registration and is enabled only by the explicit
     * operator one-shot runner used for local/runtime verification.
     */
    public AuthAccount operatorProvisionAdminAccount(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Operator-provisioned admin email is required");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Operator-provisioned admin password is required");
        }

        AuthAccount account = authAccountRepository.findByEmail(email)
                .map(existing -> resumeOperatorPasswordAccount(
                        existing, email, password, AuthAccount.Role.ADMIN))
                .orElseGet(() -> createOperatorPasswordAccount(email, password, AuthAccount.Role.ADMIN));

        if (account.getUserId() == null) {
            provisionUserProfile(account);
        }

        return account;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AuthAccount account = authAccountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Check if account is active
        if (account.getIsActive() == null || !account.getIsActive()) {
            throw new InvalidCredentialsException("Account is blocked or inactive");
        }

        if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
            throw new IllegalArgumentException("Device ID must not be empty");
        }

        requireLinkedUser(account);

        deactivateSessions(account, request.getDeviceId());

        String accessToken = tokenService.generateToken(account.getUserId(), account.getEmail(),
                account.getRole().name());
        String refreshToken = tokenService.generateRefreshToken(account.getUserId(), account.getEmail(),
                account.getRole().name());

        AuthSession session = new AuthSession();
        session.setAuthAccount(account);
        session.setDeviceId(request.getDeviceId());
        session.setDeviceName(request.getDeviceName());
        session.setDeviceType(request.getDeviceType());
        session.setIpAddress(request.getIpAddress());
        session.setRefreshToken(refreshToken);
        session.setLastLoginAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        session.setIsActive(true);

        authSessionRepository.save(session);

        return new AuthResponse(
                accessToken,
                refreshToken,
                account.getId(),
                account.getEmail(),
                account.getRole().name());
    }

    public AuthResponse socialLogin(SocialLoginRequest request) {
        if (!"google".equalsIgnoreCase(request.getProvider())) {
            throw new IllegalArgumentException("Unsupported provider: " + request.getProvider());
        }

        var payload = googleTokenVerifier.verify(request.getToken());
        String email = payload.getEmail();

        AuthAccount account = authAccountRepository.findByEmail(email).orElse(null);

        if (account == null) {
            account = new AuthAccount();
            account.setEmail(email);
            account.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));

            AuthAccount.Role role = request.getRole() == null || request.getRole().isBlank()
                    ? AuthAccount.Role.USER
                    : parsePublicRegistrationRole(request.getRole());
            account.setRole(role);
            try {
                authAccountRepository.save(account);
            } catch (DataIntegrityViolationException race) {
                account = authAccountRepository.findByEmail(email)
                        .orElseThrow(() -> race);
            }
        }

        if (account.getUserId() == null) {
            provisionUserProfile(account);
        }

        if (account.getIsActive() != null && !account.getIsActive()) {
            throw new InvalidCredentialsException("Account is blocked or inactive");
        }
        requireLinkedUser(account);

        String deviceId = request.getDeviceId() != null && !request.getDeviceId().isBlank()
                ? request.getDeviceId() : "social-device";
        deactivateSessions(account, deviceId);

        String accessToken = tokenService.generateToken(
                account.getUserId(), account.getEmail(), account.getRole().name());
        String refreshToken = tokenService.generateRefreshToken(
                account.getUserId(), account.getEmail(), account.getRole().name());

        AuthSession session = new AuthSession();
        session.setAuthAccount(account);
        session.setDeviceId(deviceId);
        session.setDeviceName(request.getDeviceName());
        try {
            session.setDeviceType(AuthSession.DeviceType.fromString(request.getDeviceType()));
        } catch (Exception ignored) {
            session.setDeviceType(AuthSession.DeviceType.MOBILE);
        }
        session.setIpAddress(request.getIpAddress());
        session.setRefreshToken(refreshToken);
        session.setLastLoginAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        session.setIsActive(true);

        authSessionRepository.save(session);

        return new AuthResponse(
                accessToken, refreshToken, account.getId(), account.getEmail(), account.getRole().name());
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String oldRefreshToken = request.getRefreshToken();

        if (!tokenService.isValid(oldRefreshToken)) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        AuthSession session = authSessionRepository.findByRefreshTokenForUpdate(oldRefreshToken)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found or expired"));

        if (!Boolean.TRUE.equals(session.getIsActive())
                || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new InvalidTokenException("Session is inactive");
        }

        AuthAccount account = session.getAuthAccount();
        if (!Boolean.TRUE.equals(account.getIsActive())) {
            throw new InvalidTokenException("Account is blocked or inactive");
        }
        requireLinkedUser(account);

        String newAccessToken = tokenService.generateToken(account.getUserId(), account.getEmail(),
                account.getRole().name());
        String newRefreshToken = tokenService.generateRefreshToken(account.getUserId(), account.getEmail(),
                account.getRole().name());

        session.setRefreshToken(newRefreshToken);
        session.setLastLoginAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        authSessionRepository.save(session);

        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                account.getId(),
                account.getEmail(),
                account.getRole().name());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (!tokenService.isValid(refreshToken)) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        authSessionRepository.findByRefreshTokenForUpdate(refreshToken).ifPresent(session -> {
            session.setIsActive(false);
            session.setExpiresAt(LocalDateTime.now());
            authSessionRepository.save(session);
        });
    }

    public List<SessionInfoResponse> getActiveSessions(String email) {
        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "email", email));

        return authSessionRepository.findActiveUnexpiredByAuthAccount(
                        account,
                        LocalDateTime.now(),
                        org.springframework.data.domain.PageRequest.of(0, 100))
                .stream()
                .map(session -> new SessionInfoResponse(
                        session.getDeviceId(),
                        session.getDeviceName(),
                        session.getDeviceType() != null ? session.getDeviceType().toString() : null,
                        session.getIpAddress(),
                        session.getLastLoginAt(),
                        session.getExpiresAt(),
                        session.getIsActive()))
                .toList();
    }

    public AuthAccountDto getAccountByIdDto(Long id) {
        AuthAccount account = authAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
        return new AuthAccountDto(account.getId(), account.getEmail(), account.getRole().name());
    }

    private void deactivateSessions(AuthAccount account, String deviceId) {
        authSessionRepository.deactivateActiveSessionsForDevice(
                account.getId(), deviceId.trim(), LocalDateTime.now());
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return org.springframework.security.core.userdetails.User.builder()
                .username(account.getEmail())
                .password(account.getPasswordHash())
                .roles(account.getRole().name())
                .build();
    }

    // Admin methods

    /**
     * Block an account (set isActive = false)
     */
    @Transactional
    public void blockAccount(Long accountId, Long adminId, String reason) {
        AuthAccount account = authAccountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        account.setIsActive(false);
        if (account.getUserId() != null) {
            markUserStatusSyncPending(account, adminId, reason);
        }
        authAccountRepository.save(account);

        authSessionRepository.deactivateAllActiveSessions(accountId, LocalDateTime.now());

        if (account.getUserId() != null) {
            scheduleUserStatusSyncAfterCommit(account.getId());
        }
    }

    /**
     * Unblock an account (set isActive = true)
     */
    @Transactional
    public void unblockAccount(Long accountId, Long adminId) {
        AuthAccount account = authAccountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        account.setIsActive(true);
        if (account.getUserId() != null) {
            markUserStatusSyncPending(account, adminId, null);
        }
        authAccountRepository.save(account);
        if (account.getUserId() != null) {
            scheduleUserStatusSyncAfterCommit(account.getId());
        }
    }

    @Scheduled(fixedDelayString = "${app.user-status-sync.poll-delay-ms:5000}")
    void reconcilePendingUserStatusSync() {
        List<AuthAccount> pendingAccounts = authAccountRepository.findPendingUserStatusSync(
                PageRequest.of(0, USER_STATUS_SYNC_BATCH_SIZE));
        for (AuthAccount account : pendingAccounts) {
            try {
                synchronizeUserStatusFromAuthSource(account);
            } catch (RuntimeException e) {
                log.warn("Pending user profile status sync failed for authAccountId={}",
                        account.getId(), e);
            }
        }
    }

    private void markUserStatusSyncPending(AuthAccount account, Long adminId, String reason) {
        account.setUserStatusSyncPending(true);
        account.setUserStatusSyncVersion((account.getUserStatusSyncVersion() == null
                ? 0L
                : account.getUserStatusSyncVersion()) + 1);
        account.setUserStatusSyncAdminId(adminId);
        account.setUserStatusSyncBlockReason(Boolean.FALSE.equals(account.getIsActive())
                ? reason
                : null);
        account.setUserStatusSyncAttempts(0);
        account.setUserStatusSyncLastError(null);
        account.setUserStatusSyncUpdatedAt(LocalDateTime.now());
    }

    private void scheduleUserStatusSyncAfterCommit(Long accountId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            synchronizeUserStatusByAccountId(accountId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                synchronizeUserStatusByAccountId(accountId);
            }
        });
    }

    private void synchronizeUserStatusByAccountId(Long accountId) {
        authAccountRepository.findById(accountId)
                .ifPresent(this::synchronizeUserStatusFromAuthSourceInNewTransaction);
    }

    private void synchronizeUserStatusFromAuthSourceInNewTransaction(AuthAccount account) {
        executeInNewTransaction(() -> synchronizeUserStatusFromAuthSource(account));
    }

    private void synchronizeUserStatusFromAuthSource(AuthAccount account) {
        if (!Boolean.TRUE.equals(account.getUserStatusSyncPending()) || account.getUserId() == null) {
            return;
        }

        Long syncVersion = account.getUserStatusSyncVersion();
        boolean blocked = !Boolean.TRUE.equals(account.getIsActive());
        try {
            syncUserBlockState(
                    account.getUserId(),
                    account.getUserStatusSyncAdminId(),
                    account.getUserStatusSyncBlockReason(),
                    blocked);
            int cleared = authAccountRepository.clearUserStatusSyncPending(
                    account.getId(), syncVersion, LocalDateTime.now());
            if (cleared == 1) {
                log.info("Synchronized user profile block state userId={}, blocked={}",
                        account.getUserId(), blocked);
            }
        } catch (RuntimeException e) {
            authAccountRepository.recordUserStatusSyncFailure(
                    account.getId(),
                    syncVersion,
                    truncateSyncError(e),
                    LocalDateTime.now());
            throw e;
        }
    }

    private void executeInNewTransaction(Runnable action) {
        if (transactionManager == null) {
            action.run();
            return;
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> action.run());
    }

    private void syncUserBlockState(Long userId, Long adminId, String reason, boolean blocked) {
        String url = blocked
                ? userServiceConfig.getBlockUserUrl(userId)
                : userServiceConfig.getUnblockUserUrl(userId);
        java.util.Map<String, String> requestBody = new java.util.HashMap<>();
        if (blocked) {
            requestBody.put("reason", reason != null ? reason : "Blocked by admin");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(adminId));
        headers.set("X-Role", "ADMIN");
        headers.set("Internal-Token", requireInternalSecret());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<BaseResponse<Void>> response = callUserService(() -> restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    new ParameterizedTypeReference<BaseResponse<Void>>() {
                    }));
            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null
                    || response.getBody().getStatus() != 1) {
                throw new IllegalStateException("User profile status synchronization was rejected");
            }
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to synchronize user profile block state", e);
        }
    }

    private String truncateSyncError(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() <= USER_STATUS_SYNC_ERROR_MAX_LENGTH
                ? message
                : message.substring(0, USER_STATUS_SYNC_ERROR_MAX_LENGTH);
    }

    private AuthAccount.Role parsePublicRegistrationRole(String role) {
        String normalized = "CUSTOMER".equalsIgnoreCase(role) ? "USER" : role.toUpperCase();
        AuthAccount.Role parsed;
        try {
            parsed = AuthAccount.Role.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + role);
        }
        if (parsed == AuthAccount.Role.ADMIN) {
            throw new IllegalArgumentException("ADMIN accounts cannot be self-registered");
        }
        if (parsed == AuthAccount.Role.SHIPPER) {
            throw new IllegalArgumentException(
                    "SHIPPER accounts require operator provisioning and profile onboarding");
        }
        return parsed;
    }

    private void requireLinkedUser(AuthAccount account) {
        if (account.getUserId() == null) {
            throw new InvalidCredentialsException("Account profile is not provisioned");
        }
    }

    private AuthAccount resumePendingPasswordRegistration(
            AuthAccount existing,
            RegisterRequest request,
            AuthAccount.Role requestedRole) {
        if (existing.getUserId() != null
                || existing.getRole() != requestedRole
                || !passwordEncoder.matches(request.getPassword(), existing.getPasswordHash())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.getEmail());
        }
        if (!Boolean.TRUE.equals(existing.getIsActive())) {
            throw new InvalidCredentialsException("Account is blocked or inactive");
        }
        log.info("Resuming user profile provisioning for authAccountId={}", existing.getId());
        return existing;
    }

    private AuthAccount createPasswordAccountOrResumeRace(
            RegisterRequest request,
            AuthAccount.Role role) {
        AuthAccount created = new AuthAccount();
        created.setEmail(request.getEmail());
        created.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        created.setRole(role);
        try {
            return authAccountRepository.save(created);
        } catch (DataIntegrityViolationException race) {
            AuthAccount concurrent = authAccountRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> race);
            return resumePendingPasswordRegistration(concurrent, request, role);
        }
    }

    private AuthAccount createOperatorPasswordAccount(
            String email,
            String password,
            AuthAccount.Role role) {
        AuthAccount created = new AuthAccount();
        created.setEmail(email);
        created.setPasswordHash(passwordEncoder.encode(password));
        created.setRole(role);
        created.setIsActive(true);
        try {
            return authAccountRepository.save(created);
        } catch (DataIntegrityViolationException race) {
            AuthAccount concurrent = authAccountRepository.findByEmail(email)
                    .orElseThrow(() -> race);
            return resumeOperatorPasswordAccount(concurrent, email, password, role);
        }
    }

    private AuthAccount resumeOperatorPasswordAccount(
            AuthAccount existing,
            String email,
            String password,
            AuthAccount.Role requestedRole) {
        if (existing.getRole() != requestedRole
                || !passwordEncoder.matches(password, existing.getPasswordHash())) {
            throw new EmailAlreadyExistsException("Email already registered: " + email);
        }
        if (!Boolean.TRUE.equals(existing.getIsActive())) {
            throw new InvalidCredentialsException("Account is blocked or inactive");
        }
        return existing;
    }

    private void provisionUserProfile(AuthAccount account) {
        CreateUserRequest userRequest = new CreateUserRequest(
                account.getId(), account.getEmail(), account.getRole().name());

        try {
            ResponseEntity<BaseResponse<UserResponse>> responseEntity = callUserService(() -> restTemplate.exchange(
                    userServiceConfig.getRegisterUrl(),
                    HttpMethod.POST,
                    internalUserRequest(userRequest),
                    new ParameterizedTypeReference<BaseResponse<UserResponse>>() {
                    }));
            BaseResponse<UserResponse> response = responseEntity.getBody();
            UserResponse user = response != null ? response.getData() : null;
            if (response == null || response.getStatus() != 1 || user == null || user.getId() == null) {
                String message = response != null ? response.getMessage() : "empty response";
                throw new IllegalStateException("User service did not provision profile: " + message);
            }
            if (!account.getId().equals(user.getAuthId())
                    || user.getEmail() == null
                    || !account.getEmail().equalsIgnoreCase(user.getEmail())
                    || !account.getRole().name().equals(user.getRole())) {
                throw new IllegalStateException(
                        "User service returned a conflicting provisioning identity");
            }
            account.setUserId(user.getId());
            authAccountRepository.save(account);
            log.info("Provisioned user profile for authAccountId={}, userId={}", account.getId(), user.getId());
        } catch (RestClientException e) {
            log.error("User profile provisioning failed for authAccountId={}", account.getId(), e);
            throw new IllegalStateException("Failed to provision user profile", e);
        }
    }

    private HttpEntity<CreateUserRequest> internalUserRequest(CreateUserRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Internal-Token", requireInternalSecret());
        return new HttpEntity<>(request, headers);
    }

    private <T> T callUserService(java.util.function.Supplier<T> supplier) {
        return userCircuitBreaker == null ? supplier.get() : userCircuitBreaker.execute(supplier);
    }

    private String requireInternalSecret() {
        String secret = userServiceConfig.getInternalSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("INTERNAL_SECRET is required for auth/user linkage");
        }
        return secret;
    }
}
