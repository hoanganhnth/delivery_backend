package com.delivery.notification_service.repository;

import com.delivery.notification_service.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    /** Atomic principal-scoped setting write for PostgreSQL replicas. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO notification_preferences (
                principal_id, marketing_notifications_enabled, updated_at
            ) VALUES (
                :principalId, :marketingNotificationsEnabled, CURRENT_TIMESTAMP
            ) ON CONFLICT (principal_id) DO UPDATE SET
                marketing_notifications_enabled = EXCLUDED.marketing_notifications_enabled,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsertPostgres(@Param("principalId") Long principalId,
                       @Param("marketingNotificationsEnabled") boolean marketingNotificationsEnabled);

    /** H2-compatible focused-test equivalent of the PostgreSQL upsert. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            MERGE INTO notification_preferences (
                principal_id, marketing_notifications_enabled, updated_at
            ) KEY (principal_id) VALUES (
                :principalId, :marketingNotificationsEnabled, CURRENT_TIMESTAMP
            )
            """, nativeQuery = true)
    int upsertH2(@Param("principalId") Long principalId,
                 @Param("marketingNotificationsEnabled") boolean marketingNotificationsEnabled);
}
