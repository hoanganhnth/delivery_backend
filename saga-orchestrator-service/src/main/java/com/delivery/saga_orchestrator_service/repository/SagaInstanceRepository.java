package com.delivery.saga_orchestrator_service.repository;

import com.delivery.saga_orchestrator_service.entity.SagaInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {

    Optional<SagaInstance> findByOrderId(Long orderId);

    Optional<SagaInstance> findByDeliveryId(Long deliveryId);

    List<SagaInstance> findByStatus(SagaInstance.SagaStatus status);

    List<SagaInstance> findByOrderIdAndSagaType(Long orderId, String sagaType);
}
