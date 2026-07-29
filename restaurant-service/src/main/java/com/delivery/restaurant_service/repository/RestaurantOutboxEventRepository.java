package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.RestaurantOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
