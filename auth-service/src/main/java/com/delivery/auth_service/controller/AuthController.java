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
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.payload.BaseResponse;
import com.delivery.auth_service.service.AuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<BaseResponse<AuthAccount>> register(@RequestBody RegisterRequest request) {
        System.out.println("Registering new account: " + request.getEmail());
        AuthAccount account = authService.register(request);
        return ResponseEntity.ok(new BaseResponse<>(1, account, "Account registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionInfoResponse>> getSessions() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(authService.getActiveSessions(email));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<AuthAccountDto> getAccountById(@PathVariable Long id) {
        AuthAccountDto dto = authService.getAccountByIdDto(id);
        return ResponseEntity.ok(dto);
    }

    /**
     * Lấy thông tin tài khoản theo email (chỉ cho nội bộ sử dụng)
     */
    @GetMapping("/accounts/email/{email}")
    public ResponseEntity<AuthAccountDto> getAccountByEmail(
            @PathVariable String email,
            @RequestHeader(value = "Internal-Token", required = false) String token) {

        if (token == null || !token.equals("GATEWAY_INTERNAL_SECRET_ABC123")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        AuthAccountDto dto = authService.getAccountByEmailDto(email);
        return ResponseEntity.ok(dto);
    }
}
