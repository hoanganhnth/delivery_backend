package com.delivery.auth_service.repository;

import com.delivery.auth_service.entity.AuthSecurityAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuthSecurityAuditRepository extends JpaRepository<AuthSecurityAudit, Long> {
    @Modifying
    @Query("delete from AuthSecurityAudit audit where audit.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
