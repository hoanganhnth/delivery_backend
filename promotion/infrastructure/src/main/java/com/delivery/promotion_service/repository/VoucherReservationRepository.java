package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.VoucherReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoucherReservationRepository extends JpaRepository<VoucherReservation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from VoucherReservation reservation "
            + "where reservation.reservationId = :reservationId")
    Optional<VoucherReservation> findByIdForUpdate(@Param("reservationId") UUID reservationId);

    Optional<VoucherReservation> findByOrderId(Long orderId);

    List<VoucherReservation> findTop100ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            VoucherReservation.State state, LocalDateTime expiresAt);
}
