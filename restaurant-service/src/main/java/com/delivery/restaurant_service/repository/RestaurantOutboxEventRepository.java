package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.RestaurantOutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RestaurantOutboxEventRepository extends JpaRepository<RestaurantOutboxEvent, Long> {
    Optional<RestaurantOutboxEvent> findTopByAggregateIdAndEventTypeOrderByCreatedAtDescIdDesc(
            String aggregateId, String eventType);

    @Query(value = """
            SELECT * FROM restaurant_outbox_events
            WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY created_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<RestaurantOutboxEvent> lockNextBatch(@Param("batchSize") int batchSize);

    @Query(value = """
            SELECT event.*
            FROM restaurant_outbox_events event
            WHERE (
                    (event.status = 'PENDING' AND event.next_attempt_at <= CURRENT_TIMESTAMP)
                    OR (event.status = 'IN_FLIGHT'
                        AND (event.lease_until IS NULL OR event.lease_until <= CURRENT_TIMESTAMP))
                  )
              AND NOT EXISTS (
                    SELECT 1
                    FROM restaurant_outbox_events predecessor
                    WHERE predecessor.aggregate_id = event.aggregate_id
                      AND predecessor.status IN ('PENDING', 'IN_FLIGHT', 'DEAD')
                      AND (predecessor.created_at < event.created_at
                        OR (predecessor.created_at = event.created_at AND predecessor.id < event.id))
                  )
            ORDER BY event.next_attempt_at, event.created_at, event.id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<RestaurantOutboxEvent> lockNextClaimableBatch(@Param("batchSize") int batchSize);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from RestaurantOutboxEvent event where event.id = :id")
    Optional<RestaurantOutboxEvent> findByIdForUpdate(@Param("id") Long id);
}
