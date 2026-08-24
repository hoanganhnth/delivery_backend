package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.MenuItemInventoryReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemInventoryReservationRepository
        extends JpaRepository<MenuItemInventoryReservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct reservation from MenuItemInventoryReservation reservation "
            + "left join fetch reservation.lines where reservation.reservationId = :id")
    Optional<MenuItemInventoryReservation> findByIdForUpdate(@Param("id") UUID id);

    @Query("select distinct reservation from MenuItemInventoryReservation reservation "
            + "left join fetch reservation.lines where reservation.orderId = :orderId")
    Optional<MenuItemInventoryReservation> findByOrderId(@Param("orderId") Long orderId);

    List<MenuItemInventoryReservation> findTop100ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            MenuItemInventoryReservation.State state, LocalDateTime expiresAt);
}
