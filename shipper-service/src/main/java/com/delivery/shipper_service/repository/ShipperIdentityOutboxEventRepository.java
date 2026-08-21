package com.delivery.shipper_service.repository;

import com.delivery.shipper_service.entity.ShipperIdentityOutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface ShipperIdentityOutboxEventRepository extends JpaRepository<ShipperIdentityOutboxEvent, Long> {
    boolean existsByEventTypeAndAggregateId(String eventType, Long aggregateId);

    /**
     * Seed and live-create share the same aggregate uniqueness boundary. A
     * check-then-save race must not poison the relay transaction when two
     * instances discover the same legacy shipper at once.
     */
    @Modifying
    @Query(value = "insert into shipper_identity_outbox_events "
            + "(event_id, event_type, aggregate_id, topic, event_key, payload, attempts, available_at, created_at, updated_at) "
            + "values (:eventId, :eventType, :aggregateId, :topic, :eventKey, cast(:payload as text), 0, current_timestamp, current_timestamp, current_timestamp) "
            + "on conflict (event_type, aggregate_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("aggregateId") Long aggregateId,
            @Param("topic") String topic,
            @Param("eventKey") String eventKey,
            @Param("payload") String payload);

    @Query("select event from ShipperIdentityOutboxEvent event where event.publishedAt is null "
            + "and event.availableAt <= :now order by event.id")
    List<ShipperIdentityOutboxEvent> findReady(LocalDateTime now, Pageable pageable);

    @Query("select count(event) from ShipperIdentityOutboxEvent event where event.publishedAt is null")
    long pendingCount();

    @Query("select min(event.createdAt) from ShipperIdentityOutboxEvent event where event.publishedAt is null")
    LocalDateTime oldestPendingCreatedAt();
}
