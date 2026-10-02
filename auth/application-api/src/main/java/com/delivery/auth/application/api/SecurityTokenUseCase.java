package com.delivery.auth.application.api;
public interface SecurityTokenUseCase {
    void requestPasswordReset(String email, String clientIp);
    void requestEmailVerification(String email, String clientIp);
    void resetPassword(String rawToken, String newPassword, String clientIp);
    void verifyEmail(String rawToken, String clientIp);
    void cleanup();
}
