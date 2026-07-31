package com.delivery.flashsale_service.repository;

import com.delivery.flashsale_service.entity.FlashSaleReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlashSaleReservationRepository extends JpaRepository<FlashSaleReservation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct reservation from FlashSaleReservation reservation "
            + "left join fetch reservation.lines where reservation.reservationId = :id")
    Optional<FlashSaleReservation> findByIdForUpdate(@Param("id") UUID id);
    @Query("select distinct reservation from FlashSaleReservation reservation "
            + "left join fetch reservation.lines where reservation.orderId = :orderId")
    Optional<FlashSaleReservation> findByOrderId(@Param("orderId") Long orderId);
    List<FlashSaleReservation> findTop100ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            FlashSaleReservation.State state, LocalDateTime expiresAt);
}
