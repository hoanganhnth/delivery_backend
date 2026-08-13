package com.delivery.match_service.repository;

import com.delivery.match_service.entity.MatchOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchOutboxEventRepository extends JpaRepository<MatchOutboxEvent, Long> {

    Optional<MatchOutboxEvent> findByEventId(UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from MatchOutboxEvent event where event.commandEventId = :commandEventId")
    List<MatchOutboxEvent> findByCommandEventIdForUpdate(@Param("commandEventId") UUID commandEventId);

    /**
     * Preserve order-result publication order for one order while allowing
     * separate relay replicas to claim independent order streams.
     */
    @Query(value = """
            SELECT event.*
            FROM match_outbox_events event
            WHERE event.status = 'PENDING'
              AND event.next_attempt_at <= CURRENT_TIMESTAMP
              AND NOT EXISTS (
                  SELECT 1
                  FROM match_outbox_events predecessor
                  WHERE predecessor.aggregate_id = event.aggregate_id
                    AND predecessor.status IN ('PENDING', 'DEAD')
                    AND (predecessor.created_at < event.created_at
                      OR (predecessor.created_at = event.created_at AND predecessor.id < event.id))
              )
            ORDER BY event.created_at, event.id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<MatchOutboxEvent> lockNextOrderedBatch(@Param("batchSize") int batchSize);
}
