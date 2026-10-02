package com.delivery.auth_service.service;
import com.delivery.auth.application.api.SecurityAuditPort;
import com.delivery.auth_service.repository.AuthSecurityAuditRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public final class SecurityAuditAdapter implements SecurityAuditPort {
    private final SecurityAuditService audit;
    private final AuthSecurityAuditRepository rows;
    @Override public void recordTransactional(Long id,String action,String outcome,String subject,String ip) {
        audit.recordTransactional(id,action,outcome,subject,ip);
    }
    @Override public void recordRejection(String action,String outcome,String ip) {
        audit.record(null,action,outcome,null,ip);
    }
    @Override public void deleteOlderThan(LocalDateTime cutoff) {rows.deleteOlderThan(cutoff);}
}
