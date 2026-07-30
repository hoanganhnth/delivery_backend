package com.delivery.delivery_service.repository;

import com.delivery.delivery_service.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    Optional<OutboxEvent> findByEventId(UUID eventId);

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY next_attempt_at, created_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("batchSize") int batchSize);
}
