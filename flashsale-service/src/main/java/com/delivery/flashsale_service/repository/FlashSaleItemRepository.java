package com.delivery.flashsale_service.repository;

import com.delivery.flashsale_service.entity.FlashSaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {
    List<FlashSaleItem> findByCampaignId(Long campaignId);
    List<FlashSaleItem> findByCampaignIsRecurringTrueAndStatus(FlashSaleItem.ItemStatus status);
}
