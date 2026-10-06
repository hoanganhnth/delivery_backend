package com.delivery.flashsale.domain;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Lazy input contracts preserve service validation ordering independently of transport validation. */
public final class FlashSaleInputs {
    private FlashSaleInputs() { }
    public interface Campaign {
        String getName(); Boolean getIsRecurring(); LocalTime getStartTime(); LocalTime getEndTime();
    }
    public interface Item {
        Long getCampaignId(); Long getRestaurantId(); Long getMenuItemId(); Integer getStockQuantity();
        BigDecimal getOriginalPrice(); BigDecimal getFlashSalePrice();
    }
    public interface Line { Long getFlashSaleItemId(); Integer getQuantity(); }
    public interface Quote { Long getRestaurantId(); List<? extends Line> getItems(); }
    public interface Reservation extends Quote {
        UUID getReservationId(); Long getOrderId(); Long getUserId(); Long getUserPrincipalId();
    }
}
