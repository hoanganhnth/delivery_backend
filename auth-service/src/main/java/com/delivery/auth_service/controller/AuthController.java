package com.delivery.auth_service.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivery.auth_service.dto.AuthAccountDto;
import com.delivery.auth_service.dto.AuthRegisterResponse;
import com.delivery.auth_service.dto.AuthResponse;
import com.delivery.auth_service.dto.BlockAccountRequest;
import com.delivery.auth_service.dto.LoginRequest;
import com.delivery.auth_service.dto.RefreshTokenRequest;
import com.delivery.auth_service.dto.RegisterRequest;
import com.delivery.auth_service.dto.SessionInfoResponse;
import com.delivery.auth_service.dto.SocialLoginRequest;
import com.delivery.auth_service.dto.SecurityEmailRequest;
import com.delivery.auth_service.dto.SecurityTokenRequest;
import com.delivery.auth_service.dto.ResetPasswordRequest;
import com.delivery.auth_service.payload.BaseResponse;
import com.delivery.auth_service.service.AuthService;
import com.delivery.auth_service.service.AccountSecurityService;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountSecurityService accountSecurityService;

    public AuthController(AuthService authService) {
        this(authService, null);
    }

    @Autowired
    public AuthController(AuthService authService, AccountSecurityService accountSecurityService) {
        this.authService = authService;
        this.accountSecurityService = accountSecurityService;
    }

    @PostMapping("/register")
    public ResponseEntity<BaseResponse<AuthRegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {
        var account = authService.register(request);
        String provisioningToken = accountSecurityService.issueUserProvisioning(account, clientIp(servletRequest));
        accountSecurityService.requestEmailVerification(account.getEmail(), clientIp(servletRequest));
        AuthRegisterResponse registration = new AuthRegisterResponse(
                account.getId(),
                account.getEmail(),
                account.getRole().name(),
                provisioningToken);
        BaseResponse<AuthRegisterResponse> response = BaseResponse.success(registration,
                "Auth identity registered; create the user profile to finish registration");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<BaseResponse<Void>> forgotPassword(
            @Valid @RequestBody SecurityEmailRequest request,
            HttpServletRequest servletRequest) {
        accountSecurityService.requestPasswordReset(request.getEmail(), clientIp(servletRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(BaseResponse.success(null, securityRequestMessage()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<BaseResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest) {
        accountSecurityService.resetPassword(
                request.getToken(), request.getNewPassword(), clientIp(servletRequest));
        return ResponseEntity.ok(BaseResponse.success(null, "Password changed successfully"));
    }

    @PostMapping("/email-verification/request")
    public ResponseEntity<BaseResponse<Void>> requestEmailVerification(
            @Valid @RequestBody SecurityEmailRequest request,
            HttpServletRequest servletRequest) {
        accountSecurityService.requestEmailVerification(request.getEmail(), clientIp(servletRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(BaseResponse.success(null, securityRequestMessage()));
    }

    @PostMapping("/email-verification/confirm")
    public ResponseEntity<BaseResponse<Void>> confirmEmailVerification(
            @Valid @RequestBody SecurityTokenRequest request,
            HttpServletRequest servletRequest) {
        accountSecurityService.verifyEmail(request.getToken(), clientIp(servletRequest));
        return ResponseEntity.ok(BaseResponse.success(null, "Email verified successfully"));
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

    @DeleteMapping("/sessions/{deviceId}")
    public ResponseEntity<BaseResponse<Void>> revokeDeviceSession(@PathVariable String deviceId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return ResponseEntity.status(401)
                    .body(BaseResponse.failure("Unauthorized"));
        }

        authService.revokeDeviceSession(authentication.getName(), deviceId);
        return ResponseEntity.ok(BaseResponse.success(null, "Device session revoked"));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<BaseResponse<AuthAccountDto>> getAccountById(@PathVariable Long id) {
        if (!isAdmin()) {
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
            @RequestBody(required = false) BlockAccountRequest request) {

        if (!isAdmin()) {
            return ResponseEntity.status(403)
                    .body(BaseResponse.failure("Only ADMIN can block accounts"));
        }

        Long adminId = getAuthenticatedAdminId();
        if (adminId == null) {
            return ResponseEntity.badRequest()
                    .body(BaseResponse.failure("Admin ID is required"));
        }

        if (request != null && request.getReason() != null && request.getReason().length() > 500) {
            return ResponseEntity.badRequest()
                    .body(BaseResponse.failure("Block reason must not exceed 500 characters"));
        }

        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Blocked by admin";

        authService.blockAccount(id, adminId, reason);
        return ResponseEntity.ok(BaseResponse.success(null, "Account blocked successfully"));
    }

    /**
     * Unblock an account (admin only)
     */
    @PostMapping("/admin/accounts/{id}/unblock")
    public ResponseEntity<BaseResponse<Void>> unblockAccount(@PathVariable Long id) {

        if (!isAdmin()) {
            return ResponseEntity.status(403)
                    .body(BaseResponse.failure("Only ADMIN can unblock accounts"));
        }

        Long adminId = getAuthenticatedAdminId();
        if (adminId == null) {
            return ResponseEntity.badRequest()
                    .body(BaseResponse.failure("Admin ID is required"));
        }

        authService.unblockAccount(id, adminId);
        return ResponseEntity.ok(BaseResponse.success(null, "Account unblocked successfully"));
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        if (authentication.getPrincipal() instanceof com.delivery.auth.resourceserver.security.AuthenticatedActor actor) {
            return actor.isAdmin();
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ADMIN".equals(authority.getAuthority()));
    }

    private Long getAuthenticatedAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            if (authentication.getPrincipal() instanceof com.delivery.auth.resourceserver.security.AuthenticatedActor actor) {
                if (actor.getUserId() != null) return actor.getUserId();
                if (actor.getEmail() != null && !actor.getEmail().isBlank()) {
                    var account = authService.getAccountByEmail(actor.getEmail());
                    if (account.isPresent()) return account.get().getId();
                }
            }
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                try {
                    return Long.parseLong(jwt.getSubject());
                } catch (NumberFormatException ignored) {}
                String email = jwt.getClaimAsString("email");
                if (email != null && !email.isBlank()) {
                    var account = authService.getAccountByEmail(email);
                    if (account.isPresent()) return account.get().getId();
                }
            }
            if (authentication.getName() != null && !authentication.getName().isBlank()) {
                if (authentication.getName().matches("\\d+")) {
                    return Long.parseLong(authentication.getName());
                }
                var account = authService.getAccountByEmail(authentication.getName());
                if (account.isPresent()) return account.get().getId();
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    private String securityRequestMessage() {
        return "If the account is eligible, an email will be sent";
    }
}
