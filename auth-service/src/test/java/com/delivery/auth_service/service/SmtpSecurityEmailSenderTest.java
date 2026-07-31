package com.delivery.auth_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SmtpSecurityEmailSenderTest {
    @Test
    void sendsResetAndVerificationLinksThroughConfiguredSmtpSender() {
        JavaMailSender mail = mock(JavaMailSender.class);
        SmtpSecurityEmailSender sender = new SmtpSecurityEmailSender(
                mail, "no-reply@example.com",
                "https://app.example.com/reset-password",
                "https://app.example.com/verify-email");

        sender.sendPasswordReset("user@example.com", "reset-token-value");
        sender.sendEmailVerification("user@example.com", "verify-token-value");

        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, org.mockito.Mockito.times(2)).send(messages.capture());
        assertThat(messages.getAllValues().get(0).getText())
                .contains("https://app.example.com/reset-password?token=reset-token-value");
        assertThat(messages.getAllValues().get(1).getText())
                .contains("https://app.example.com/verify-email?token=verify-token-value");
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.getFrom()).isEqualTo("no-reply@example.com");
            assertThat(message.getTo()).containsExactly("user@example.com");
        });
    }

    @Test
    void missingSenderAddressFailsBeforeCallingProvider() {
        JavaMailSender mail = mock(JavaMailSender.class);
        SmtpSecurityEmailSender sender = new SmtpSecurityEmailSender(
                mail, "", "https://app/reset", "https://app/verify");

        assertThatThrownBy(() -> sender.sendPasswordReset("user@example.com", "token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sender address");
        verify(mail, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }
}
