package com.delivery.auth_service.repository;

import com.delivery.auth_service.entity.IdentityOutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IdentityOutboxEventRepository extends JpaRepository<IdentityOutboxEvent, Long> {
    @Query("select event from IdentityOutboxEvent event where event.publishedAt is null and event.availableAt <= :now order by event.id")
    List<IdentityOutboxEvent> findReady(LocalDateTime now, Pageable pageable);

    @Query("select count(event) from IdentityOutboxEvent event where event.publishedAt is null")
    long pendingCount();

    @Query("select min(event.createdAt) from IdentityOutboxEvent event where event.publishedAt is null")
    LocalDateTime oldestPendingCreatedAt();
}
