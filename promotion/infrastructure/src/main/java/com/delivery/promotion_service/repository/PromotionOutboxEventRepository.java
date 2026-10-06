package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.PromotionOutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PromotionOutboxEventRepository extends JpaRepository<PromotionOutboxEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from PromotionOutboxEvent event "
            + "where event.status = :status and event.nextAttemptAt <= :now "
            + "order by event.createdAt, event.eventId")
    List<PromotionOutboxEvent> lockDue(@Param("status") PromotionOutboxEvent.Status status,
                                       @Param("now") LocalDateTime now,
                                       Pageable pageable);
}
