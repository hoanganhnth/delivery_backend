package com.delivery.flashsale_service.repository;

import com.delivery.flashsale_service.entity.FlashSaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {
    List<FlashSaleItem> findByCampaignId(Long campaignId, Pageable pageable);
    List<FlashSaleItem> findByCampaignIdAndStatus(
            Long campaignId, FlashSaleItem.ItemStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FlashSaleItem item set item.soldQuantity = 0 "
            + "where item.campaign.isRecurring = true and item.status = :status "
            + "and item.soldQuantity <> 0")
    int resetRecurringSoldQuantity(@Param("status") FlashSaleItem.ItemStatus status);
}
