package com.delivery.auth_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_security_audit")
public class AuthSecurityAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_id")
    private Long authId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(name = "subject_hash", length = 64)
    private String subjectHash;

    @Column(name = "client_ip_hash", length = 64)
    private String clientIpHash;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getAuthId() { return authId; }
    public void setAuthId(Long authId) { this.authId = authId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getSubjectHash() { return subjectHash; }
    public void setSubjectHash(String subjectHash) { this.subjectHash = subjectHash; }
    public String getClientIpHash() { return clientIpHash; }
    public void setClientIpHash(String clientIpHash) { this.clientIpHash = clientIpHash; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
