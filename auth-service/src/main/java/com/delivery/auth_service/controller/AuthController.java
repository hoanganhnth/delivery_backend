package com.delivery.auth_service.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
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
import com.delivery.auth_service.dto.BlockAccountRequest;
import com.delivery.auth_service.dto.LoginRequest;
import com.delivery.auth_service.dto.RefreshTokenRequest;
import com.delivery.auth_service.dto.RegisterRequest;
import com.delivery.auth_service.dto.SessionInfoResponse;
import com.delivery.auth_service.dto.SocialLoginRequest;
import com.delivery.auth_service.payload.BaseResponse;
import com.delivery.auth_service.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<BaseResponse<Boolean>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        BaseResponse<Boolean> response = BaseResponse.success(true, "Account registered successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<BaseResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        BaseResponse<AuthResponse> response = BaseResponse.success(authResponse, "Login successful");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/social-login")
    public ResponseEntity<BaseResponse<AuthResponse>> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        AuthResponse authResponse = authService.socialLogin(request);
        BaseResponse<AuthResponse> response = BaseResponse.success(authResponse, "Social login successful");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<BaseResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse authResponse = authService.refreshToken(request);
        BaseResponse<AuthResponse> response = BaseResponse.success(authResponse, "Token refreshed");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        BaseResponse<Void> response = BaseResponse.success(null, "Logout successful");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    public ResponseEntity<BaseResponse<List<SessionInfoResponse>>> getSessions() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return ResponseEntity.status(401)
                    .body(BaseResponse.failure("Unauthorized"));
        }

        String email = authentication.getName();
        List<SessionInfoResponse> sessions = authService.getActiveSessions(email);
        return ResponseEntity.ok(BaseResponse.success(sessions));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<BaseResponse<AuthAccountDto>> getAccountById(
            @PathVariable Long id,
            @RequestHeader(value = "X-Role", required = false) String role) {
        if (!isAdmin(role)) {
            return ResponseEntity.status(403)
                    .body(BaseResponse.failure("Only ADMIN can access this endpoint"));
        }

        AuthAccountDto dto = authService.getAccountByIdDto(id);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // Admin endpoints

    /**
     * Block an account (admin only)
     */
    @PostMapping("/admin/accounts/{id}/block")
    public ResponseEntity<BaseResponse<Void>> blockAccount(
            @PathVariable Long id,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody(required = false) BlockAccountRequest request) {

        if (!isAdmin(role)) {
            return ResponseEntity.status(403)
                    .body(BaseResponse.failure("Only ADMIN can block accounts"));
        }

        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(BaseResponse.failure("Admin ID is required"));
        }

        if (request != null && request.getReason() != null && request.getReason().length() > 500) {
            return ResponseEntity.badRequest()
                    .body(BaseResponse.failure("Block reason must not exceed 500 characters"));
        }

        Long adminId = userId;
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Blocked by admin";

        authService.blockAccount(id, adminId, reason);
        return ResponseEntity.ok(BaseResponse.success(null, "Account blocked successfully"));
    }

    /**
     * Unblock an account (admin only)
     */
    @PostMapping("/admin/accounts/{id}/unblock")
    public ResponseEntity<BaseResponse<Void>> unblockAccount(
            @PathVariable Long id,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (!isAdmin(role)) {
            return ResponseEntity.status(403)
                    .body(BaseResponse.failure("Only ADMIN can unblock accounts"));
        }

        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(BaseResponse.failure("Admin ID is required"));
        }

        authService.unblockAccount(id, userId);
        return ResponseEntity.ok(BaseResponse.success(null, "Account unblocked successfully"));
    }

    private boolean isAdmin(String role) {
        if (role != null) {
            return "ADMIN".equals(role);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ADMIN".equals(authority.getAuthority()));
    }
}
