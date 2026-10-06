package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import com.delivery.flashsale_service.entity.FlashSaleItem;

import java.time.LocalTime;

/** Availability decisions only; the caller owns locking and stock changes. */
final class FlashSaleAvailabilityPolicy {
    private FlashSaleAvailabilityPolicy() { }

    static void requireAvailable(FlashSaleItem item, Long restaurantId, int quantity, LocalTime now) {
        com.delivery.flashsale.domain.FlashSaleAvailabilityPolicy.requireAvailable(
                new com.delivery.flashsale.domain.FlashSaleAvailabilityPolicy.Item() {
                    public boolean deleted() { return item.getDeletedAt() != null; }
                    public Long restaurantId() { return item.getRestaurantId(); }
                    public boolean approved() { return item.getStatus() == FlashSaleItem.ItemStatus.APPROVED; }
                    public boolean campaignActive() {
                        return item.getCampaign().getStatus() == FlashSaleCampaign.CampaignStatus.ACTIVE;
                    }
                    public LocalTime campaignStartTime() { return item.getCampaign().getStartTime(); }
                    public LocalTime campaignEndTime() { return item.getCampaign().getEndTime(); }
                    public Integer stockQuantity() { return item.getStockQuantity(); }
                    public Integer soldQuantity() { return item.getSoldQuantity(); }
                    public Long id() { return item.getId(); }
                }, restaurantId, quantity, now);
    }
}
