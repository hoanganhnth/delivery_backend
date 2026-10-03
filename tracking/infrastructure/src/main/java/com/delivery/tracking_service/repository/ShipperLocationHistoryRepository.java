package com.delivery.tracking_service.repository;

import com.delivery.tracking_service.entity.ShipperLocationHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShipperLocationHistoryRepository
        extends JpaRepository<ShipperLocationHistory, Long> {

    Optional<ShipperLocationHistory>
    findTopByDeliveryIdAndShipperIdAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(
            Long deliveryId, Long shipperId, Instant occurredAt);

    Optional<ShipperLocationHistory>
    findTopByDeliveryIdAndShipperIdAndOccurredAtGreaterThanEqualOrderByOccurredAtAscIdAsc(
            Long deliveryId, Long shipperId, Instant occurredAt);

    List<ShipperLocationHistory> findByDeliveryIdOrderByOccurredAtAscIdAsc(
            Long deliveryId, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ShipperLocationHistory h where h.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
