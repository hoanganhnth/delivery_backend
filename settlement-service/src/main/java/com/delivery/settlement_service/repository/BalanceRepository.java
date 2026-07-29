package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface BalanceRepository extends JpaRepository<Balance, Long> {

    Optional<Balance> findByEntityIdAndEntityType(Long entityId, EntityType entityType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Balance b where b.entityId = :entityId and b.entityType = :entityType")
    Optional<Balance> findByEntityIdAndEntityTypeForUpdate(
            @Param("entityId") Long entityId,
            @Param("entityType") EntityType entityType);

}
