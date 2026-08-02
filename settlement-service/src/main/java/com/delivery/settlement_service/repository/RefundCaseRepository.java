package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.RefundCase;
import com.delivery.settlement_service.entity.RefundCase.RefundComponent;
import com.delivery.settlement_service.entity.RefundCase.RefundStatus;
import com.delivery.settlement_service.entity.RefundCase.RefundTrigger;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundCaseRepository extends JpaRepository<RefundCase, UUID> {
    Optional<RefundCase> findByEventId(UUID eventId);

    Optional<RefundCase> findByIdempotencyKey(String idempotencyKey);

    Optional<RefundCase> findByOrderIdAndTriggerAndComponent(
            Long orderId, RefundTrigger trigger, RefundComponent component);

    List<RefundCase> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<RefundCase> findByStatusOrderByCreatedAtDesc(RefundStatus status, Pageable pageable);
}
