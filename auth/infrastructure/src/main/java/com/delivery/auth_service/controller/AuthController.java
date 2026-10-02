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
import org.springframework.web.server.ResponseStatusException;

import com.delivery.auth_service.dto.AuthAccountDto;
import com.delivery.auth_service.dto.AuthRegisterResponse;
import com.delivery.auth_service.dto.AuthResponse;
import com.delivery.auth_service.dto.FirebaseChatTokenResponse;
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
import com.delivery.auth.domain.policy.FirebaseChatUnavailable;
import com.delivery.auth_service.dto.RegistrationStatusResponse;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final com.delivery.auth.application.api.SecurityTokenUseCase security;
    private final com.delivery.auth.application.api.AccountLifecycleUseCase lifecycle;
    private final com.delivery.auth.application.api.FirebaseChatTokenUseCase firebase;
    private final com.delivery.auth.application.api.AccountLookupUseCase accounts;
    private final com.delivery.auth.application.api.RegistrationUseCase registrationUseCase;
    private final com.delivery.auth.application.api.LoginUseCase loginUseCase;

    private final com.delivery.auth.application.api.RefreshTokenUseCase refreshTokenUseCase;
    private final com.delivery.auth.application.api.LogoutUseCase logoutUseCase;

    private final com.delivery.auth.application.api.DeviceSessionUseCase deviceSessionUseCase;

    private final com.delivery.auth.application.api.RegistrationRecoveryUseCase registrationRecoveryUseCase;

    private final com.delivery.auth.application.api.RegistrationAdmissionUseCase registrationAdmissionUseCase;

    private final com.delivery.auth.application.api.SocialLoginUseCase socialLoginUseCase;

    @Autowired
    public AuthController(com.delivery.auth.application.api.SecurityTokenUseCase security,
            com.delivery.auth.application.api.AccountLifecycleUseCase lifecycle,
            com.delivery.auth.application.api.FirebaseChatTokenUseCase firebase, com.delivery.auth.application.api.RegistrationUseCase registrationUseCase, com.delivery.auth.application.api.LoginUseCase loginUseCase, com.delivery.auth.application.api.RefreshTokenUseCase refreshTokenUseCase, com.delivery.auth.application.api.LogoutUseCase logoutUseCase, com.delivery.auth.application.api.DeviceSessionUseCase deviceSessionUseCase, com.delivery.auth.application.api.RegistrationRecoveryUseCase registrationRecoveryUseCase, com.delivery.auth.application.api.RegistrationAdmissionUseCase registrationAdmissionUseCase, com.delivery.auth.application.api.SocialLoginUseCase socialLoginUseCase,
            com.delivery.auth.application.api.AccountLookupUseCase accounts) {
        this.security = security;
        this.lifecycle = lifecycle;
        this.firebase = firebase;
        this.accounts = accounts;
        this.registrationUseCase = registrationUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.deviceSessionUseCase = deviceSessionUseCase;
        this.registrationRecoveryUseCase = registrationRecoveryUseCase;
        this.registrationAdmissionUseCase = registrationAdmissionUseCase;
        this.socialLoginUseCase = socialLoginUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<BaseResponse<AuthRegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {
        // Public password registration is an Auth -> User two-request flow. Do
        // not create an identity until the paired profile outbox/consumer path
        // is enabled; otherwise a Wave-1 deployment would manufacture accounts
        // that cannot complete onboarding or log in.
        if (!registrationAdmissionUseCase.admits(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Registration is temporarily unavailable while onboarding is being deployed");
        }
        var result = registrationUseCase.register(new com.delivery.auth.application.api.RegisterCommand(
                request.getEmail(), request.getPassword(), request.getRole()));
        var account = result.account();
        security.requestEmailVerification(account.email(), clientIp(servletRequest));
        AuthRegisterResponse registration = new AuthRegisterResponse(
                account.id(), account.email(), account.role().name(), result.provisioningToken(),
                result.registrationHandle(), result.registrationHandleExpiresAt(),
                com.delivery.identity.contracts.IdentityLifecycleStatus.valueOf(account.lifecycleStatus().name()));
        BaseResponse<AuthRegisterResponse> response = BaseResponse.success(registration,
                "Auth identity registered; create the user profile to finish registration");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/registrations/{handle}")
    public ResponseEntity<BaseResponse<RegistrationStatusResponse>> registrationStatus(@PathVariable String handle) {
        var result = registrationRecoveryUseCase.status(handle);
        RegistrationStatusResponse status = new RegistrationStatusResponse(result.principalId(),
                com.delivery.identity.contracts.IdentityLifecycleStatus.valueOf(result.status().name()),
                result.nextAction(), result.profileLinked(), result.expiresAt());
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("Retry-After", status.status().name().startsWith("PENDING") ? "3" : "0")
                .body(BaseResponse.success(status));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<BaseResponse<Void>> forgotPassword(
            @Valid @RequestBody SecurityEmailRequest request,
            HttpServletRequest servletRequest) {
        security.requestPasswordReset(request.getEmail(), clientIp(servletRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(BaseResponse.success(null, securityRequestMessage()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<BaseResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest) {
        security.resetPassword(
                request.getToken(), request.getNewPassword(), clientIp(servletRequest));
        return ResponseEntity.ok(BaseResponse.success(null, "Password changed successfully"));
    }

    @PostMapping("/email-verification/request")
    public ResponseEntity<BaseResponse<Void>> requestEmailVerification(
            @Valid @RequestBody SecurityEmailRequest request,
            HttpServletRequest servletRequest) {
        security.requestEmailVerification(request.getEmail(), clientIp(servletRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(BaseResponse.success(null, securityRequestMessage()));
    }

    @PostMapping("/email-verification/confirm")
    public ResponseEntity<BaseResponse<Void>> confirmEmailVerification(
            @Valid @RequestBody SecurityTokenRequest request,
            HttpServletRequest servletRequest) {
        security.verifyEmail(request.getToken(), clientIp(servletRequest));
        return ResponseEntity.ok(BaseResponse.success(null, "Email verified successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<BaseResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        var result = loginUseCase.login(new com.delivery.auth.application.api.LoginCommand(
                request.getEmail(), request.getPassword(), request.getDeviceId(), request.getDeviceName(),
                request.getDeviceType() == null ? null : com.delivery.auth.domain.model.Session.DeviceType.valueOf(request.getDeviceType().name()),
                request.getIpAddress()));
        AuthResponse authResponse = new AuthResponse(result.accessToken(), result.refreshToken(), result.authId(), result.email(), result.role());
        BaseResponse<AuthResponse> response = BaseResponse.success(authResponse, "Login successful");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/social-login")
    public ResponseEntity<BaseResponse<AuthResponse>> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        com.delivery.auth.domain.model.Session.DeviceType deviceType;
        try { deviceType = com.delivery.auth.domain.model.Session.DeviceType.fromString(request.getDeviceType()); }
        catch (IllegalArgumentException invalid) { deviceType = com.delivery.auth.domain.model.Session.DeviceType.MOBILE; }
        var result = socialLoginUseCase.login(new com.delivery.auth.application.api.SocialLoginCommand(
                request.getProvider(), request.getToken(), request.getRole(), request.getDeviceId(),
                request.getDeviceName(), deviceType, request.getIpAddress()));
        AuthResponse authResponse = new AuthResponse(result.accessToken(), result.refreshToken(), result.authId(), result.email(), result.role());
        BaseResponse<AuthResponse> response = BaseResponse.success(authResponse, "Social login successful");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<BaseResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        var result = refreshTokenUseCase.refresh(new com.delivery.auth.application.api.RefreshTokenCommand(request.getRefreshToken()));
        AuthResponse authResponse = new AuthResponse(result.accessToken(), result.refreshToken(), result.authId(), result.email(), result.role());
        BaseResponse<AuthResponse> response = BaseResponse.success(authResponse, "Token refreshed");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/firebase/chat-token")
    public ResponseEntity<BaseResponse<FirebaseChatTokenResponse>> firebaseChatToken(
        Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof com.delivery.auth.resourceserver.security.AuthenticatedActor actor)
                || (!actor.isUser() && !actor.isAdmin())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.failure("Support chat is not available for this identity"));
        }

        try {
            return ResponseEntity.ok(BaseResponse.success(
                    firebaseResponse(actor),
                    "Firebase chat token created"));
        } catch (FirebaseChatUnavailable exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(BaseResponse.failure("Support chat is temporarily unavailable"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        logoutUseCase.logout(request.getRefreshToken());
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
        List<SessionInfoResponse> sessions = deviceSessionUseCase.activeSessions(email).stream()
                .map(session -> new SessionInfoResponse(session.deviceId(), session.deviceName(),
                        session.deviceType() == null ? null : session.deviceType().toString(), session.ipAddress(),
                        session.lastLoginAt(), session.expiresAt(), session.isActive())).toList();
        return ResponseEntity.ok(BaseResponse.success(sessions));
    }

    @DeleteMapping("/sessions/{deviceId}")
    public ResponseEntity<BaseResponse<Void>> revokeDeviceSession(@PathVariable String deviceId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return ResponseEntity.status(401)
                    .body(BaseResponse.failure("Unauthorized"));
        }

        deviceSessionUseCase.revokeDevice(authentication.getName(), deviceId);
        return ResponseEntity.ok(BaseResponse.success(null, "Device session revoked"));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<BaseResponse<AuthAccountDto>> getAccountById(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403)
                    .body(BaseResponse.failure("Only ADMIN can access this endpoint"));
        }

        var account = accounts.requireById(id);
        AuthAccountDto dto = new AuthAccountDto(account.id(), account.email(), account.role().name());
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

        lifecycle.block(id, adminId, reason);
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

        lifecycle.unblock(id, adminId);
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
                if (actor.getPrincipalId() != null) return actor.getPrincipalId();
                if (actor.getEmail() != null && !actor.getEmail().isBlank()) {
                    var account = accounts.byEmail(actor.getEmail());
                    if (account.isPresent()) return account.get().id();
                }
            }
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                Object principal = jwt.getClaim("principal_id");
                if (principal instanceof Number number && number.longValue() > 0) return number.longValue();
                if (principal instanceof String value && value.matches("\\d+")) {
                    try { return Long.parseLong(value); } catch (NumberFormatException ignored) {}
                }
                String email = jwt.getClaimAsString("email");
                if (email != null && !email.isBlank()) {
                    var account = accounts.byEmail(email);
                    if (account.isPresent()) return account.get().id();
                }
            }
            if (authentication.getName() != null && !authentication.getName().isBlank()) {
                if (authentication.getName().matches("\\d+")) {
                    return Long.parseLong(authentication.getName());
                }
                var account = accounts.byEmail(authentication.getName());
                if (account.isPresent()) return account.get().id();
            }
        }
        return null;
    }

    private FirebaseChatTokenResponse firebaseResponse(
            com.delivery.auth.resourceserver.security.AuthenticatedActor actor) {
        var result=firebase.issue(new com.delivery.auth.application.api.FirebaseChatTokenUseCase.Actor(
                actor.getPrincipalId(),actor.getRoles(),actor.isAdmin()));
        return new FirebaseChatTokenResponse(result.token(),result.expiresInSeconds(),result.principalId(),result.role());
    }

    private String clientIp(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    private String securityRequestMessage() {
        return "If the account is eligible, an email will be sent";
    }
}
