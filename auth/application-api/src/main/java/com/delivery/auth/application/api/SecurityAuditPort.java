package com.delivery.auth.application.api;
import java.time.LocalDateTime;
public interface SecurityAuditPort {
    void recordTransactional(Long accountId, String action, String outcome, String subject, String clientIp);
    void recordRejection(String action, String outcome, String clientIp);
    void deleteOlderThan(LocalDateTime cutoff);
}
