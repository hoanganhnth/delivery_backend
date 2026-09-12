package com.delivery.livestream_service.repository;

import com.delivery.livestream_service.entity.LivestreamModerationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LivestreamModerationAuditRepository extends JpaRepository<LivestreamModerationAudit, Long> {
}
