package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.CatalogLifecycleAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogLifecycleAuditRepository extends JpaRepository<CatalogLifecycleAudit, Long> {
}
