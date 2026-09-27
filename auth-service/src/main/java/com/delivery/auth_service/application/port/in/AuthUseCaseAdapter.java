package com.delivery.auth_service.application.port.in;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth_service.dto.*;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.service.*;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Compatibility adapter while the legacy auth implementation is migrated behind ports. */
@Service
public class AuthUseCaseAdapter implements AuthUseCase, JwksUseCase, PrincipalLookupUseCase {
    private final AuthService auth;
    private final AccountSecurityService security;
    private final TokenService tokens;
    private final IdentityRegistrationService registrations;
    private final RegistrationAdmissionPolicy admission;
    private final FirebaseChatTokenService firebase;
    private final PrincipalLookupService principals;

    public AuthUseCaseAdapter(AuthService auth, AccountSecurityService security, TokenService tokens,
            IdentityRegistrationService registrations, RegistrationAdmissionPolicy admission,
            FirebaseChatTokenService firebase, PrincipalLookupService principals) {
        this.auth = auth;
        this.security = security;
        this.tokens = tokens;
        this.registrations = registrations;
        this.admission = admission;
        this.firebase = firebase;
        this.principals = principals;
    }

    @Override public AuthAccount register(RegisterRequest request) { return auth.register(request); }
    @Override public IdentityRegistrationService.IssuedHandle issueRegistration(AuthAccount account) { return registrations.issue(account); }
    @Override public RegistrationStatusResponse registrationStatus(String handle) { return registrations.status(handle); }
    @Override public boolean admitsRegistration(String email) { return admission.admits(email); }
    @Override public String generateProvisioningToken(AuthAccount account) { return tokens.generateProvisioningToken(account.getId(), account.getEmail(), account.getRole().name()); }
    @Override public void requestPasswordReset(String email, String ip) { security.requestPasswordReset(email, ip); }
    @Override public void resetPassword(String token, String password, String ip) { security.resetPassword(token, password, ip); }
    @Override public void requestEmailVerification(String email, String ip) { security.requestEmailVerification(email, ip); }
    @Override public void verifyEmail(String token, String ip) { security.verifyEmail(token, ip); }
    @Override public AuthResponse login(LoginRequest request) { return auth.login(request); }
    @Override public AuthResponse socialLogin(SocialLoginRequest request) { return auth.socialLogin(request); }
    @Override public AuthResponse refreshToken(RefreshTokenRequest request) { return auth.refreshToken(request); }
    @Override public void logout(String refreshToken) { auth.logout(refreshToken); }
    @Override public List<SessionInfoResponse> activeSessions(String email) { return auth.getActiveSessions(email); }
    @Override public void revokeDeviceSession(String email, String deviceId) { auth.revokeDeviceSession(email, deviceId); }
    @Override public AuthAccountDto accountById(Long id) { return auth.getAccountByIdDto(id); }
    @Override public Optional<AuthAccount> accountByEmail(String email) { return auth.getAccountByEmail(email); }
    @Override public void blockAccount(Long id, Long adminId, String reason) { auth.blockAccount(id, adminId, reason); }
    @Override public void unblockAccount(Long id, Long adminId) { auth.unblockAccount(id, adminId); }
    @Override public FirebaseChatTokenResponse issueFirebaseChatToken(AuthenticatedActor actor) { return firebase.issue(actor); }
    @Override public java.util.Map<String, Object> getJwks() { return tokens.getJwks(); }
    @Override public Optional<com.delivery.identity.contracts.IdentityPrincipal> findByPrincipalId(Long id) { return principals.findByPrincipalId(id); }
}
