package com.delivery.restaurant_service.repository;
// Package retained during the infrastructure migration to preserve existing callers.

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
    // Lock the root row before reading its lines. DISTINCT/fetch joins trigger
    // follow-on locking on PostgreSQL, which can retain a pre-lock state snapshot.
    @Query("select reservation from MenuItemInventoryReservation reservation where reservation.reservationId = :id")
    Optional<MenuItemInventoryReservation> findByIdForUpdate(@Param("id") UUID id);

    @Query("select distinct reservation from MenuItemInventoryReservation reservation "
            + "left join fetch reservation.lines where reservation.orderId = :orderId")
    Optional<MenuItemInventoryReservation> findByOrderId(@Param("orderId") Long orderId);

    // Candidate IDs avoid retaining pre-lock entity snapshots in the expiry transaction.
    @Query("select reservation.reservationId from MenuItemInventoryReservation reservation "
            + "where reservation.state = :state and reservation.expiresAt <= :expiresAt order by reservation.expiresAt")
    List<UUID> findDueReservationIds(@Param("state") MenuItemInventoryReservation.State state,
            @Param("expiresAt") LocalDateTime expiresAt, org.springframework.data.domain.Pageable page);
}
