package com.delivery.auth_service.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.delivery.auth_service.config.UserServiceConfig;
import com.delivery.auth_service.dto.AuthAccountDto;
import com.delivery.auth_service.dto.AuthResponse;
import com.delivery.auth_service.dto.CreateUserRequest;
import com.delivery.auth_service.dto.LoginRequest;
import com.delivery.auth_service.dto.RefreshTokenRequest;
import com.delivery.auth_service.dto.RegisterRequest;
import com.delivery.auth_service.dto.SessionInfoResponse;
import com.delivery.auth_service.dto.SocialLoginRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.Collections;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final AuthAccountRepository authAccountRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserServiceConfig userServiceConfig;
    private final RestTemplate restTemplate;

    /**
     * Đăng ký tài khoản mới
     */
    @Transactional
    public AuthAccount register(RegisterRequest request) {
        // 1. Kiểm tra email đã tồn tại
        if (authAccountRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.getEmail());
        }

        // 2. Kiểm tra role
        if (request.getRole() == null || request.getRole().isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }

        AuthAccount.Role roleEnum;
        try {
            roleEnum = AuthAccount.Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + request.getRole());
        }

        // 3. Tạo AuthAccount
        AuthAccount account = new AuthAccount();
        account.setEmail(request.getEmail());
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setRole(roleEnum);

        authAccountRepository.save(account);

        // 4. Gọi user-service để tạo user
        CreateUserRequest userRequest = new CreateUserRequest(
                account.getId(),
                account.getEmail(),
                account.getRole().name());

        try {
            ResponseEntity<BaseResponse<UserResponse>> responseEntity = restTemplate.exchange(
                    userServiceConfig.getRegisterUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(userRequest),
                    new ParameterizedTypeReference<BaseResponse<UserResponse>>() {
                    });

            BaseResponse<UserResponse> response = responseEntity.getBody();

            if (response == null || response.getStatus() != 1) {
                String msg = (response != null) ? response.getMessage() : "Unknown error";
                throw new RuntimeException("Failed to create user in user-service: " + msg);
            }

            UserResponse userResponse = response.getData();

            // 5. Update userId lại cho AuthAccount
            if (userResponse != null && userResponse.getId() != null) {
                account.setUserId(userResponse.getId());
                authAccountRepository.save(account);
            } else {
                throw new RuntimeException("Failed to create user in user-service: userId is null");
            }

            System.out.println("✅ User created in user-service with userId = " + userResponse.getId());

        } catch (RestClientException e) {
            System.err.println("❌ Failed to create user in user-service: " + e.getMessage());
            throw new RuntimeException("Failed to create user in user-service", e);
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

    @Transactional
    public AuthResponse socialLogin(SocialLoginRequest request) {
        if (!"google".equalsIgnoreCase(request.getProvider())) {
            throw new IllegalArgumentException("Unsupported provider: " + request.getProvider());
        }

        try {
            // Xác thực token Google
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .build(); // Add .setAudience(Collections.singletonList("YOUR_CLIENT_ID")) for production

            // Dùng parse để bỏ qua check signature nếu cần (cho development linh hoạt)
            GoogleIdToken idToken = null;
            try {
                idToken = verifier.verify(request.getToken());
            } catch (Exception e) {}
            
            if (idToken == null) {
                idToken = GoogleIdToken.parse(new GsonFactory(), request.getToken());
                if (idToken == null) throw new InvalidCredentialsException("Invalid token format.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();

            // Kiểm tra tài khoản
            AuthAccount account = authAccountRepository.findByEmail(email).orElse(null);

            if (account == null) {
                // Auto-create tài khoản mới
                account = new AuthAccount();
                account.setEmail(email);
                account.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
                
                AuthAccount.Role role = AuthAccount.Role.USER;
                if (request.getRole() != null && !request.getRole().isBlank()) {
                    try {
                        role = AuthAccount.Role.valueOf(request.getRole().toUpperCase());
                    } catch (Exception ignored) {
                        if ("CUSTOMER".equalsIgnoreCase(request.getRole())) {
                            role = AuthAccount.Role.USER;
                        }
                    }
                }
                account.setRole(role);
                authAccountRepository.save(account);

                // Tạo profile bên user-service
                CreateUserRequest userRequest = new CreateUserRequest(
                        account.getId(), account.getEmail(), account.getRole().name());
                
                try {
                    ResponseEntity<BaseResponse<UserResponse>> responseEntity = restTemplate.exchange(
                            userServiceConfig.getRegisterUrl(),
                            HttpMethod.POST,
                            new HttpEntity<>(userRequest),
                            new ParameterizedTypeReference<BaseResponse<UserResponse>>() {}
                    );
                    BaseResponse<UserResponse> res = responseEntity.getBody();
                    if (res != null && res.getStatus() == 1 && res.getData() != null) {
                        account.setUserId(res.getData().getId());
                        authAccountRepository.save(account);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to create user in user-service for social login: " + e.getMessage());
                }
            }

            if (account.getIsActive() != null && !account.getIsActive()) {
                throw new InvalidCredentialsException("Account is blocked or inactive");
            }

            // Quản lý session
            String deviceId = request.getDeviceId() != null && !request.getDeviceId().isBlank() 
                                ? request.getDeviceId() : "social-device";
            deactivateSessions(account, deviceId);

            String accessToken = tokenService.generateToken(account.getUserId(), account.getEmail(), account.getRole().name());
            String refreshToken = tokenService.generateRefreshToken(account.getUserId(), account.getEmail(), account.getRole().name());

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

            return new AuthResponse(accessToken, refreshToken, account.getId(), account.getEmail(), account.getRole().name());

        } catch (Exception e) {
            throw new InvalidCredentialsException("Social login failed: " + e.getMessage());
        }
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String oldRefreshToken = request.getRefreshToken();

        if (!tokenService.isValid(oldRefreshToken)) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        AuthSession session = authSessionRepository.findByRefreshToken(oldRefreshToken)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found or expired"));

        if (!session.getIsActive()) {
            throw new InvalidTokenException("Session is inactive");
        }

        AuthAccount account = session.getAuthAccount();

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

    public void logout(String refreshToken) {
        if (!tokenService.isValid(refreshToken)) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        authSessionRepository.findByRefreshToken(refreshToken).ifPresent(session -> {
            session.setIsActive(false);
            authSessionRepository.save(session);
        });
    }

    public List<SessionInfoResponse> getActiveSessions(String email) {
        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "email", email));

        return authSessionRepository.findByAuthAccount(account).stream()
                .map(session -> new SessionInfoResponse(
                        session.getDeviceId(),
                        session.getDeviceName(),
                        session.getDeviceType().toString(),
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
        String trimmed = deviceId.trim();
        var sessions = authSessionRepository.findByAuthAccountAndDeviceIdAndIsActiveTrue(account, trimmed);
        sessions.forEach(s -> {
            s.setIsActive(false);
            s.setExpiresAt(LocalDateTime.now());
            authSessionRepository.save(s);
        });
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

    public AuthAccountDto getAccountByEmailDto(String email) {
        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "email", email));
        return new AuthAccountDto(account.getId(), account.getEmail(), account.getRole().name());
    }

    // Admin methods

    /**
     * Block an account (set isActive = false)
     */
    @Transactional
    public void blockAccount(Long accountId, Long adminId, String reason) {
        AuthAccount account = authAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        account.setIsActive(false);
        authAccountRepository.save(account);

        // Deactivate all active sessions
        List<AuthSession> activeSessions = authSessionRepository.findByAuthAccount(account).stream()
                .filter(AuthSession::getIsActive)
                .toList();

        activeSessions.forEach(session -> {
            session.setIsActive(false);
            session.setExpiresAt(LocalDateTime.now());
            authSessionRepository.save(session);
        });

        // 3. Call user-service to block user
        if (account.getUserId() != null) {
            try {
                String url = userServiceConfig.getBlockUserUrl(account.getUserId());

                // Create request body
                java.util.Map<String, String> requestBody = new java.util.HashMap<>();
                requestBody.put("reason", reason != null ? reason : "Blocked by admin");

                // Create headers with admin ID
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("X-User-Id", String.valueOf(adminId));
                headers.set("X-Role", "ADMIN");
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

                HttpEntity<java.util.Map<String, String>> request = new HttpEntity<>(requestBody, headers);

                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        request,
                        new ParameterizedTypeReference<BaseResponse<Void>>() {
                        });

                System.out.println("✅ User blocked in user-service: userId=" + account.getUserId());
            } catch (Exception e) {
                System.err.println("⚠️ Failed to block user in user-service: " + e.getMessage());
                // Don't fail the whole operation if user-service call fails
            }
        }
    }

    /**
     * Unblock an account (set isActive = true)
     */
    @Transactional
    public void unblockAccount(Long accountId) {
        AuthAccount account = authAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        account.setIsActive(true);
        authAccountRepository.save(account);
    }

    /**
     * Get all accounts (for admin)
     */
    public List<AuthAccountDto> getAllAccounts() {
        return authAccountRepository.findAll().stream()
                .map(account -> new AuthAccountDto(account.getId(), account.getEmail(), account.getRole().name()))
                .toList();
    }
}
