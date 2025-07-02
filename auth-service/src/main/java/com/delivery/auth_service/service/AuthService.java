package com.delivery.auth_service.service;

import com.delivery.auth_service.dto.AuthResponse;
import com.delivery.auth_service.dto.LoginRequest;
import com.delivery.auth_service.dto.RefreshTokenRequest;
import com.delivery.auth_service.dto.RegisterRequest;
import com.delivery.auth_service.dto.SessionInfoResponse;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.AuthSession;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.AuthSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;

import java.time.LocalDateTime;

@Service
public class AuthService implements UserDetailsService {

    @Autowired
    private AuthAccountRepository authAccountRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    // Đăng ký tài khoản mới
    public void register(RegisterRequest request) {
        if (authAccountRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        AuthAccount account = new AuthAccount();
        account.setEmail(request.getEmail());
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setRole(
                request.getRole() != null ? request.getRole() : AuthAccount.Role.USER);
        authAccountRepository.save(account);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        System.out.println("Login called with email: " + request.getEmail());

        AuthAccount account = authAccountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));
        System.out.println("Account found: " + account.getEmail());

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            System.out.println("Password mismatch");
            throw new RuntimeException("Invalid email or password");
        }
        System.out.println("Password matched");

        String accessToken = tokenService.generateToken(account.getEmail());
        String refreshToken = tokenService.generateRefreshToken(account.getEmail());
        System.out.println("Tokens generated");

        AuthSession session = new AuthSession();
        session.setAuthAccount(account);
        session.setDeviceId(request.getDeviceId());
        session.setDeviceName(request.getDeviceName());
        // set enum trực tiếp
        session.setDeviceType(request.getDeviceType());
        session.setIpAddress(request.getIpAddress());
        session.setRefreshToken(refreshToken);
        session.setLastLoginAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        session.setIsActive(true);

        authSessionRepository.save(session);
        System.out.println("Session saved");

        return new AuthResponse(accessToken, refreshToken);
    }

    // Refresh token: kiểm tra refreshToken hợp lệ, cập nhật session với
    // refreshToken mới
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

        String email = tokenService.extractEmail(oldRefreshToken);
        String newAccessToken = tokenService.generateToken(email);
        String newRefreshToken = tokenService.generateRefreshToken(email);

        // Cập nhật session với refresh token mới
        session.setRefreshToken(newRefreshToken);
        session.setLastLoginAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(7));

        authSessionRepository.save(session);

        return new AuthResponse(newAccessToken, newRefreshToken);
    }

    // Đăng xuất: set session isActive = false
    public void logout(String refreshToken) {
        if (!tokenService.isValid(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        authSessionRepository.findByRefreshToken(refreshToken).ifPresent(session -> {
            session.setIsActive(false);
            authSessionRepository.save(session);
        });
    }

    // Load user by username (email) để Spring Security xử lý authentication
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

    // Lấy thông tin các phiên đăng nhập đang hoạt động của người dùng
    public List<SessionInfoResponse> getActiveSessions(String email) {
        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<AuthSession> sessions = authSessionRepository.findByAuthAccount(account);

        return sessions.stream()
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

}
