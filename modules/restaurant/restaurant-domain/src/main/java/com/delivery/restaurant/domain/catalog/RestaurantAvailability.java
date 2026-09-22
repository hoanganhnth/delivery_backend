package com.delivery.restaurant.domain.catalog;

import java.time.Instant;

public final class RestaurantAvailability {

    private final RestaurantStatus restaurantStatus;
    private final OperatingSchedule operatingSchedule;

    public RestaurantAvailability(
            RestaurantStatus restaurantStatus,
            OperatingSchedule operatingSchedule) {
        if (restaurantStatus == null) {
            throw new CatalogDomainException(
                    CatalogRuleViolation.RESTAURANT_STATUS_REQUIRED,
                    "Restaurant status is required");
        }
        if (operatingSchedule == null) {
            throw new CatalogDomainException(
                    CatalogRuleViolation.OPERATING_SCHEDULE_REQUIRED,
                    "Operating schedule is required");
        }
        this.restaurantStatus = restaurantStatus;
        this.operatingSchedule = operatingSchedule;
    }

    public boolean isPubliclyVisible() {
        return restaurantStatus != RestaurantStatus.ARCHIVED;
    }

    public boolean isMenuPubliclyVisible(MenuItemStatus menuItemStatus) {
        return isPubliclyVisible() && menuItemStatus == MenuItemStatus.AVAILABLE;
    }

    public boolean acceptsCheckout(MenuItemStatus menuItemStatus, Instant instant) {
        if (instant == null) {
            throw new CatalogDomainException(
                    CatalogRuleViolation.INSTANT_REQUIRED,
                    "Availability instant is required");
        }
        return restaurantStatus == RestaurantStatus.ACTIVE
                && menuItemStatus == MenuItemStatus.AVAILABLE
                && operatingSchedule.isOpenAt(instant);
    }
}
