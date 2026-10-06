package com.delivery.flashsale_service.repository;

import com.delivery.flashsale_service.entity.FlashSaleOutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface FlashSaleOutboxEventRepository extends JpaRepository<FlashSaleOutboxEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from FlashSaleOutboxEvent event where event.status = :status "
            + "and event.nextAttemptAt <= :now order by event.createdAt, event.eventId")
    List<FlashSaleOutboxEvent> lockDue(@Param("status") FlashSaleOutboxEvent.Status status,
                                       @Param("now") LocalDateTime now, Pageable pageable);
}
