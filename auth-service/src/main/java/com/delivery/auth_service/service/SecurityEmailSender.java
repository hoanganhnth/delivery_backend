package com.delivery.auth_service.service;

public interface SecurityEmailSender {
    void sendPasswordReset(String recipient, String rawToken);
    void sendEmailVerification(String recipient, String rawToken);
}
