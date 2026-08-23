package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.CodCapacityHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface CodCapacityHoldRepository extends JpaRepository<CodCapacityHold, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hold from CodCapacityHold hold where hold.holdId = :holdId")
    Optional<CodCapacityHold> findByIdForUpdate(@Param("holdId") UUID holdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hold from CodCapacityHold hold where hold.idempotencyKey = :key")
    Optional<CodCapacityHold> findByIdempotencyKeyForUpdate(@Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hold from CodCapacityHold hold where hold.status = com.delivery.settlement_service.entity.CodCapacityHoldStatus.HELD and hold.expiresAt <= :now order by hold.expiresAt asc")
    List<CodCapacityHold> findExpiredHeldForUpdate(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hold from CodCapacityHold hold where hold.deliveryId = :deliveryId and hold.status in (com.delivery.settlement_service.entity.CodCapacityHoldStatus.HELD, com.delivery.settlement_service.entity.CodCapacityHoldStatus.COMMITTED)")
    List<CodCapacityHold> findActiveByDeliveryIdForUpdate(@Param("deliveryId") Long deliveryId);
}
