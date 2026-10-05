package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.PromotionReservationLine;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PromotionReservationLineRepository extends JpaRepository<PromotionReservationLine, Long> {
    List<PromotionReservationLine> findByReservationIdOrderByVoucherIdAsc(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select line from PromotionReservationLine line where line.reservationId = :reservationId "
            + "order by line.voucherId asc")
    List<PromotionReservationLine> findByReservationIdForUpdateOrderByVoucherIdAsc(
            @Param("reservationId") UUID reservationId);
}
