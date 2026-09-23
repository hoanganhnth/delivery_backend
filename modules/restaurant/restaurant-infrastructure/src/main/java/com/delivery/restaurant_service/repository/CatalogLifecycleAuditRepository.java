package com.delivery.restaurant_service.repository;
// Package retained during migration to preserve JPA scanning and existing callers.

import com.delivery.restaurant_service.entity.CatalogLifecycleAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogLifecycleAuditRepository extends JpaRepository<CatalogLifecycleAudit, Long> {
}
