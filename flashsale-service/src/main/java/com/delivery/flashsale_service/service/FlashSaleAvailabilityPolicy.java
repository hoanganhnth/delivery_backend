package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import com.delivery.flashsale_service.entity.FlashSaleItem;

import java.time.LocalTime;

/** Availability decisions only; the caller owns locking and stock changes. */
final class FlashSaleAvailabilityPolicy {
    private FlashSaleAvailabilityPolicy() { }

    static void requireAvailable(FlashSaleItem item, Long restaurantId, int quantity, LocalTime now) {
        if (item.getDeletedAt() != null)
            throw new IllegalArgumentException("Flash sale item is deleted");
        if (!item.getRestaurantId().equals(restaurantId))
            throw new IllegalArgumentException("Flash sale item belongs to another restaurant");
        if (item.getStatus() != FlashSaleItem.ItemStatus.APPROVED)
            throw new IllegalArgumentException("Flash sale item is not approved");
        FlashSaleCampaign campaign = item.getCampaign();
        if (campaign.getStatus() != FlashSaleCampaign.CampaignStatus.ACTIVE
                || now.isBefore(campaign.getStartTime()) || now.isAfter(campaign.getEndTime()))
            throw new IllegalArgumentException("Flash sale campaign is not active");
        if (item.getStockQuantity() - item.getSoldQuantity() < quantity)
            throw new IllegalArgumentException("Out of stock for flash sale item " + item.getId());
    }
}
