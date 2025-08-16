package com.delivery.auth_service.service;

import java.time.LocalDateTime;
import java.util.List;

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
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.AuthSession;
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
    if (authAccountRepository.findByEmail(request.getEmail()).isPresent()) {
        throw new RuntimeException("Email already registered");
    }

    if (request.getRole() == null || request.getRole().isBlank()) {
        throw new RuntimeException("Role is required");
    }

    AuthAccount.Role roleEnum;
    try {
        roleEnum = AuthAccount.Role.valueOf(request.getRole().toUpperCase());
    } catch (IllegalArgumentException e) {
        throw new RuntimeException("Invalid role: " + request.getRole());
    }

    AuthAccount account = new AuthAccount();
    account.setEmail(request.getEmail());
    account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    account.setRole(roleEnum);

    authAccountRepository.save(account);

    CreateUserRequest userRequest = new CreateUserRequest(
        account.getId(),
        account.getEmail(),
        account.getRole().name()
    );

    try {
        restTemplate.postForObject(userServiceConfig.getRegisterUrl(), userRequest, Void.class);
        System.out.println("✅ User created in user-service");
    } catch (RestClientException e) {
        System.err.println("❌ Failed to create user in user-service: " + e.getMessage());
        throw new RuntimeException("Failed to create user in user-service", e);
    }
    return account;
}

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AuthAccount account = authAccountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
            throw new RuntimeException("Device ID must not be empty");
        }

        deactivateSessions(account, request.getDeviceId());

        String accessToken = tokenService.generateToken(account.getEmail());
        String refreshToken = tokenService.generateRefreshToken(account.getEmail());

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
                account.getRole().name()
        );
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String oldRefreshToken = request.getRefreshToken();

        if (!tokenService.isValid(oldRefreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        AuthSession session = authSessionRepository.findByRefreshToken(oldRefreshToken)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getIsActive()) {
            throw new RuntimeException("Session is inactive");
        }

        AuthAccount account = session.getAuthAccount();

        String newAccessToken = tokenService.generateToken(account.getEmail());
        String newRefreshToken = tokenService.generateRefreshToken(account.getEmail());

        session.setRefreshToken(newRefreshToken);
        session.setLastLoginAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        authSessionRepository.save(session);

        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                account.getId(),
                account.getEmail(),
                account.getRole().name()
        );
    }

    public void logout(String refreshToken) {
        if (!tokenService.isValid(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        authSessionRepository.findByRefreshToken(refreshToken).ifPresent(session -> {
            session.setIsActive(false);
            authSessionRepository.save(session);
        });
    }

    public List<SessionInfoResponse> getActiveSessions(String email) {
        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return authSessionRepository.findByAuthAccount(account).stream()
                .map(session -> new SessionInfoResponse(
                        session.getDeviceId(),
                        session.getDeviceName(),
                        session.getDeviceType().toString(),
                        session.getIpAddress(),
                        session.getLastLoginAt(),
                        session.getExpiresAt(),
                        session.getIsActive()
                ))
                .toList();
    }

    public AuthAccountDto getAccountByIdDto(Long id) {
        AuthAccount account = authAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AuthAccount not found: " + id));
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
                .orElseThrow(() -> new RuntimeException("AuthAccount not found with email: " + email));
        return new AuthAccountDto(account.getId(), account.getEmail(), account.getRole().name());
    }
}
