package com.delivery.tracking_service.repository;

import com.delivery.tracking_service.entity.LocationHistoryReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface LocationHistoryReceiptRepository
        extends JpaRepository<LocationHistoryReceipt, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LocationHistoryReceipt r where r.processedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
