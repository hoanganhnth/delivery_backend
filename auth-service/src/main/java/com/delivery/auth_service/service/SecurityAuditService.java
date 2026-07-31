package com.delivery.auth_service.service;

import com.delivery.auth_service.entity.AuthSecurityAudit;
import com.delivery.auth_service.repository.AuthSecurityAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class SecurityAuditService {
    private final AuthSecurityAuditRepository audits;

    public SecurityAuditService(AuthSecurityAuditRepository audits) {
        this.audits = audits;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long authId, String action, String outcome, String subject, String clientIp) {
        save(authId, action, outcome, subject, clientIp);
    }

    @Transactional
    public void recordTransactional(
            Long authId, String action, String outcome, String subject, String clientIp) {
        save(authId, action, outcome, subject, clientIp);
    }

    private void save(Long authId, String action, String outcome, String subject, String clientIp) {
        AuthSecurityAudit audit = new AuthSecurityAudit();
        audit.setAuthId(authId);
        audit.setAction(action);
        audit.setOutcome(outcome);
        audit.setSubjectHash(hashNullable(subject));
        audit.setClientIpHash(hashNullable(clientIp));
        audits.save(audit);
    }

    public String hashNullable(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
