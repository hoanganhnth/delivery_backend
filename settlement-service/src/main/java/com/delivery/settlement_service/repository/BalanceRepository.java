package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BalanceRepository extends JpaRepository<Balance, Long> {

    Optional<Balance> findByEntityIdAndEntityType(Long entityId, EntityType entityType);

    boolean existsByEntityIdAndEntityType(Long entityId, EntityType entityType);

    List<Balance> findByEntityType(EntityType entityType);
}
