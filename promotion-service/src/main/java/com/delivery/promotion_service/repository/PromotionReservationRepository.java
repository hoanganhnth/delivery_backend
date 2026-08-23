package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.PromotionReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionReservationRepository extends JpaRepository<PromotionReservation, UUID> {
    Optional<PromotionReservation> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from PromotionReservation reservation where reservation.reservationId = :id")
    Optional<PromotionReservation> findByIdForUpdate(@Param("id") UUID id);

    List<PromotionReservation> findTop100ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            PromotionReservation.State state, LocalDateTime expiresAt);
}
