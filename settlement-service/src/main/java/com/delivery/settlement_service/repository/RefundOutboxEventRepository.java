package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.RefundOutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RefundOutboxEventRepository extends JpaRepository<RefundOutboxEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from RefundOutboxEvent event where event.status = :status "
            + "and event.nextAttemptAt <= :now order by event.createdAt, event.eventId")
    List<RefundOutboxEvent> lockDue(@Param("status") RefundOutboxEvent.Status status,
                                    @Param("now") LocalDateTime now,
                                    Pageable pageable);
}
