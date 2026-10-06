package com.delivery.flashsale.domain;

import java.time.LocalTime;

public final class FlashSaleAvailabilityPolicy {
    private FlashSaleAvailabilityPolicy() { }

    public interface Item {
        boolean deleted();
        Long restaurantId();
        boolean approved();
        boolean campaignActive();
        LocalTime campaignStartTime();
        LocalTime campaignEndTime();
        Integer stockQuantity();
        Integer soldQuantity();
        Long id();
    }

    public static void requireAvailable(Item item, Long restaurantId, int quantity, LocalTime now) {
        if (item.deleted())
            throw new IllegalArgumentException("Flash sale item is deleted");
        if (!item.restaurantId().equals(restaurantId))
            throw new IllegalArgumentException("Flash sale item belongs to another restaurant");
        if (!item.approved())
            throw new IllegalArgumentException("Flash sale item is not approved");
        if (!item.campaignActive()
                || now.isBefore(item.campaignStartTime()) || now.isAfter(item.campaignEndTime()))
            throw new IllegalArgumentException("Flash sale campaign is not active");
        if (item.stockQuantity() - item.soldQuantity() < quantity)
            throw new IllegalArgumentException("Out of stock for flash sale item " + item.id());
    }
}
