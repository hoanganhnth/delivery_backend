package com.delivery.auth_service.application.port.in;

import com.delivery.auth_service.dto.*;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.service.IdentityRegistrationService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import java.util.List;
import java.util.Optional;

/** Inbound application port used by the HTTP auth host. */
public interface AuthUseCase {
    AuthAccount register(RegisterRequest request);
    IdentityRegistrationService.IssuedHandle issueRegistration(AuthAccount account);
    RegistrationStatusResponse registrationStatus(String handle);
    boolean admitsRegistration(String email);
    String generateProvisioningToken(AuthAccount account);
    void requestPasswordReset(String email, String clientIp);
    void resetPassword(String token, String password, String clientIp);
    void requestEmailVerification(String email, String clientIp);
    void verifyEmail(String token, String clientIp);
    AuthResponse login(LoginRequest request);
    AuthResponse socialLogin(SocialLoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(String refreshToken);
    List<SessionInfoResponse> activeSessions(String email);
    void revokeDeviceSession(String email, String deviceId);
    AuthAccountDto accountById(Long id);
    Optional<AuthAccount> accountByEmail(String email);
    void blockAccount(Long id, Long adminId, String reason);
    void unblockAccount(Long id, Long adminId);
    FirebaseChatTokenResponse issueFirebaseChatToken(AuthenticatedActor actor);
}
