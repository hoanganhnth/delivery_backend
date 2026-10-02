package com.delivery.auth_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class SmtpSecurityEmailSender implements SecurityEmailSender {
    private final JavaMailSender mailSender;
    private final String from;
    private final String resetBaseUrl;
    private final String verificationBaseUrl;

    public SmtpSecurityEmailSender(
            JavaMailSender mailSender,
            @Value("${app.security-email.from:}") String from,
            @Value("${app.security-email.password-reset-url}") String resetBaseUrl,
            @Value("${app.security-email.verification-url}") String verificationBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.resetBaseUrl = resetBaseUrl;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    @Override
    public void sendPasswordReset(String recipient, String rawToken) {
        send(recipient, "Reset your Delivery password",
                "Use this one-time link within 15 minutes:\n" + link(resetBaseUrl, rawToken));
    }

    @Override
    public void sendEmailVerification(String recipient, String rawToken) {
        send(recipient, "Verify your Delivery email",
                "Verify this account within 24 hours:\n" + link(verificationBaseUrl, rawToken));
    }

    private void send(String recipient, String subject, String body) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("Security email sender address is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private String link(String baseUrl, String rawToken) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("token", rawToken)
                .build().encode().toUriString();
    }
}
