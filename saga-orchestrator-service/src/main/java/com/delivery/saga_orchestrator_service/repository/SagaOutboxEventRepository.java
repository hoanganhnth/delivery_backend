package com.delivery.saga_orchestrator_service.repository;

import com.delivery.saga_orchestrator_service.entity.SagaOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SagaOutboxEventRepository extends JpaRepository<SagaOutboxEvent, Long> {
    /**
     * Claim at most the earliest unsent command for each order. An earlier retry
     * (or DEAD command awaiting operator action) blocks later commands for that
     * order, preserving per-order Kafka ordering across relay instances.
     */
    @Query(value = """
            SELECT event.*
            FROM saga_outbox_events event
            WHERE event.status = 'PENDING'
              AND event.next_attempt_at <= CURRENT_TIMESTAMP
              AND NOT EXISTS (
                  SELECT 1
                  FROM saga_outbox_events predecessor
                  WHERE predecessor.aggregate_id = event.aggregate_id
                    AND predecessor.status IN ('PENDING', 'DEAD')
                    AND (predecessor.created_at < event.created_at
                      OR (predecessor.created_at = event.created_at AND predecessor.id < event.id))
              )
            ORDER BY event.created_at, event.id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<SagaOutboxEvent> lockNextOrderedBatch(@Param("batchSize") int batchSize);
}
