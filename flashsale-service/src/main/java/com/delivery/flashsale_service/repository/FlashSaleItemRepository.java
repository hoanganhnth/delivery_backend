package com.delivery.flashsale_service.repository;

import com.delivery.flashsale_service.entity.FlashSaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from FlashSaleItem item join fetch item.campaign "
            + "where item.id in :ids order by item.id")
    List<FlashSaleItem> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
    List<FlashSaleItem> findByCampaignId(Long campaignId, Pageable pageable);
    List<FlashSaleItem> findByCampaignIdAndStatus(
            Long campaignId, FlashSaleItem.ItemStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FlashSaleItem item set item.soldQuantity = 0 "
            + "where item.campaign.isRecurring = true and item.status = :status "
            + "and item.soldQuantity <> 0")
    int resetRecurringSoldQuantity(@Param("status") FlashSaleItem.ItemStatus status);
}
