package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.PaymentOrder;
import com.delivery.settlement_service.entity.PaymentOrder.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByPaymentRef(String paymentRef);

    List<PaymentOrder> findByEntityIdAndEntityTypeOrderByCreatedAtDesc(Long entityId, EntityType entityType);

    List<PaymentOrder> findByStatus(PaymentStatus status);

    /**
     * Tìm các payment đã hết hạn nhưng vẫn PENDING (để job cleanup)
     */
    List<PaymentOrder> findByStatusAndExpiredAtBefore(PaymentStatus status, LocalDateTime now);
}
