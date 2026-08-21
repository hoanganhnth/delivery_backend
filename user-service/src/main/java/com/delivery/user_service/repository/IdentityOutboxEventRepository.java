package com.delivery.user_service.repository;

import com.delivery.user_service.entity.IdentityOutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface IdentityOutboxEventRepository extends JpaRepository<IdentityOutboxEvent, Long> {
    /**
     * Atomic idempotency guard for profile-created. Checking then inserting loses
     * to concurrent retries; PostgreSQL resolves that race without poisoning the
     * surrounding profile transaction.
     */
    @Modifying
    @Query(value = "insert into identity_outbox_events "
            + "(event_id, event_type, aggregate_id, topic, event_key, payload, attempts, available_at, created_at, updated_at) "
            + "values (:eventId, :eventType, :aggregateId, :topic, :eventKey, cast(:payload as text), 0, current_timestamp, current_timestamp, current_timestamp) "
            + "on conflict (event_type, aggregate_id) do nothing", nativeQuery = true)
    int insertProfileCreatedIfAbsent(@Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("aggregateId") Long aggregateId,
            @Param("topic") String topic,
            @Param("eventKey") String eventKey,
            @Param("payload") String payload);
    @Query("select event from IdentityOutboxEvent event where event.publishedAt is null and event.availableAt <= :now order by event.id")
    List<IdentityOutboxEvent> findReady(LocalDateTime now, Pageable pageable);

    @Query("select count(event) from IdentityOutboxEvent event where event.publishedAt is null")
    long pendingCount();

    @Query("select min(event.createdAt) from IdentityOutboxEvent event where event.publishedAt is null")
    LocalDateTime oldestPendingCreatedAt();
}
