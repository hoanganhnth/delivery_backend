package com.delivery.auth_service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivery.auth_service.dto.AuthAccountDto;
import com.delivery.auth_service.dto.AuthResponse;
import com.delivery.auth_service.dto.LoginRequest;
import com.delivery.auth_service.dto.RefreshTokenRequest;
import com.delivery.auth_service.dto.RegisterRequest;
import com.delivery.auth_service.dto.SessionInfoResponse;
import com.delivery.auth_service.payload.BaseResponse;
import com.delivery.auth_service.service.AuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<BaseResponse<Boolean>> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        BaseResponse<Boolean> response = new BaseResponse<>(1, "Account registered successfully", true);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<BaseResponse<AuthResponse>> login(@RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        BaseResponse<AuthResponse> response = new BaseResponse<>(1, "Login successful", authResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<BaseResponse<AuthResponse>> refreshToken(@RequestBody RefreshTokenRequest request) {
        AuthResponse authResponse = authService.refreshToken(request);
        BaseResponse<AuthResponse> response = new BaseResponse<>(1, "Token refreshed", authResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        BaseResponse<Void> response = new BaseResponse<>(1, "Logout successful", null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    public ResponseEntity<BaseResponse<List<SessionInfoResponse>>> getSessions() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<SessionInfoResponse> sessions = authService.getActiveSessions(email);
        return ResponseEntity.ok(new BaseResponse<>(1, sessions));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<BaseResponse<AuthAccountDto>> getAccountById(@PathVariable Long id) {
        AuthAccountDto dto = authService.getAccountByIdDto(id);
        return ResponseEntity.ok(new BaseResponse<>(1, dto));
    }

    /**
     * Lấy thông tin tài khoản theo email (chỉ cho nội bộ sử dụng)
     */
    @GetMapping("/accounts/email/{email}")
    public ResponseEntity<BaseResponse<AuthAccountDto>> getAccountByEmail(
            @PathVariable String email,
            @RequestHeader(value = "Internal-Token", required = false) String token) {

        if (token == null || !token.equals("GATEWAY_INTERNAL_SECRET_ABC123")) {
            BaseResponse<AuthAccountDto> response = new BaseResponse<>(0, "Forbidden", null);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        AuthAccountDto dto = authService.getAccountByEmailDto(email);
        return ResponseEntity.ok(new BaseResponse<>(1, dto));
    }
}
