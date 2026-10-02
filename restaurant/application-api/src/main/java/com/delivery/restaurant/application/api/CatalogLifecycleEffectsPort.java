package com.delivery.restaurant.application.api;

public interface CatalogLifecycleEffectsPort {
    record Audit(String aggregateType, Long aggregateId, String action, Long actorPrincipalId,
            String actorRole, String beforeStatus, String afterStatus, long beforeVersion, long afterVersion) { }
    void recordAudit(Audit audit);
    void publishRestaurant(Long id, String action);
    void publishMenuItem(Long id, String action);
    void missingExpectedVersion();
    void legacyOwnershipFallback();
}
