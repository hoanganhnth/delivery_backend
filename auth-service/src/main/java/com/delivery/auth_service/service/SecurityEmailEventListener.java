package com.delivery.auth_service.service;

import com.delivery.auth_service.entity.AuthSecurityToken.Purpose;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SecurityEmailEventListener {
    private final SecurityEmailSender sender;
    private final SecurityAuditService audit;

    public SecurityEmailEventListener(SecurityEmailSender sender, SecurityAuditService audit) {
        this.sender = sender;
        this.audit = audit;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(SecurityEmailEvent event) {
        String action = event.purpose() == Purpose.PASSWORD_RESET
                ? "PASSWORD_RESET_REQUEST" : "EMAIL_VERIFICATION_REQUEST";
        try {
            if (event.purpose() == Purpose.PASSWORD_RESET) {
                sender.sendPasswordReset(event.recipient(), event.rawToken());
            } else {
                sender.sendEmailVerification(event.recipient(), event.rawToken());
            }
            audit.record(event.authId(), action, "DELIVERED", event.recipient(), event.clientIp());
        } catch (RuntimeException deliveryFailure) {
            audit.record(event.authId(), action, "DELIVERY_FAILED", event.recipient(), event.clientIp());
        }
    }
}
