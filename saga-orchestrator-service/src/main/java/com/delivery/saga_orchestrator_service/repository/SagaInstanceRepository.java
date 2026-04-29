package com.delivery.saga_orchestrator_service.repository;

import com.delivery.saga_orchestrator_service.entity.SagaInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {

    Optional<SagaInstance> findByOrderId(Long orderId);

    Optional<SagaInstance> findByDeliveryId(Long deliveryId);

    List<SagaInstance> findByStatus(SagaInstance.SagaStatus status);

    List<SagaInstance> findByOrderIdAndSagaType(Long orderId, String sagaType);

    /**
     * ✅ TỐI ƯU: Tìm các saga bị kẹt trực tiếp bằng SQL
     * Chỉ lấy các đơn có status phù hợp và updatedAt cũ hơn thời gian cutoff
     */
    @Query("SELECT s FROM SagaInstance s WHERE s.status = :status AND s.updatedAt < :cutoff")
    List<SagaInstance> findStuckSagas(
            @Param("status") SagaInstance.SagaStatus status,
            @Param("cutoff") LocalDateTime cutoff);
}
