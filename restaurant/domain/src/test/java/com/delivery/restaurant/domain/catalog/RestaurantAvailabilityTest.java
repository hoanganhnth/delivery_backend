package com.delivery.restaurant.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RestaurantAvailabilityTest {

    private final Instant lunch = Instant.parse("2026-09-22T05:00:00Z");
    private final OperatingSchedule lunchHours = OperatingSchedule.of(
            LocalTime.of(8, 0), LocalTime.of(22, 0), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Test
    void activeRestaurantAcceptsAvailableMenuItemDuringOperatingHours() {
        RestaurantAvailability availability = new RestaurantAvailability(RestaurantStatus.ACTIVE, lunchHours);

        assertTrue(availability.isPubliclyVisible());
        assertTrue(availability.acceptsCheckout(MenuItemStatus.AVAILABLE, lunch));
    }

    @Test
    void pausedRestaurantRemainsVisibleButRejectsNewCheckout() {
        RestaurantAvailability availability = new RestaurantAvailability(RestaurantStatus.PAUSED, lunchHours);

        assertTrue(availability.isPubliclyVisible());
        assertFalse(availability.acceptsCheckout(MenuItemStatus.AVAILABLE, lunch));
    }

    @Test
    void menuIsPublicOnlyWhenAvailableAndRestaurantIsNotArchived() {
        for (RestaurantStatus restaurantStatus : RestaurantStatus.values()) {
            RestaurantAvailability availability = new RestaurantAvailability(restaurantStatus, lunchHours);
            for (MenuItemStatus menuStatus : MenuItemStatus.values()) {
                boolean expected = restaurantStatus != RestaurantStatus.ARCHIVED
                        && menuStatus == MenuItemStatus.AVAILABLE;
                assertEquals(expected, availability.isMenuPubliclyVisible(menuStatus),
                        restaurantStatus + " / " + menuStatus);
            }
            assertFalse(availability.isMenuPubliclyVisible(null), restaurantStatus.toString());
        }
    }

    @Test
    void archivedRestaurantIsHiddenAndRejectsNewCheckout() {
        RestaurantAvailability availability = new RestaurantAvailability(RestaurantStatus.ARCHIVED, lunchHours);

        assertFalse(availability.isPubliclyVisible());
        assertFalse(availability.acceptsCheckout(MenuItemStatus.AVAILABLE, lunch));
    }

    @Test
    void checkoutRejectsClosedRestaurantOrNonAvailableMenuItem() {
        RestaurantAvailability availability = new RestaurantAvailability(RestaurantStatus.ACTIVE, lunchHours);

        assertFalse(availability.acceptsCheckout(MenuItemStatus.AVAILABLE,
                Instant.parse("2026-09-22T16:00:00Z")));
        assertFalse(availability.acceptsCheckout(MenuItemStatus.SOLD_OUT, lunch));
        assertFalse(availability.acceptsCheckout(MenuItemStatus.DISCONTINUED, lunch));
        assertFalse(availability.acceptsCheckout(MenuItemStatus.ARCHIVED, lunch));
        assertFalse(availability.acceptsCheckout(null, lunch));
    }

    @Test
    void availabilityRejectsMissingRequiredInputs() {
        assertEquals(CatalogRuleViolation.RESTAURANT_STATUS_REQUIRED,
                assertThrows(CatalogDomainException.class,
                        () -> new RestaurantAvailability(null, lunchHours)).violation());
        assertEquals(CatalogRuleViolation.OPERATING_SCHEDULE_REQUIRED,
                assertThrows(CatalogDomainException.class,
                        () -> new RestaurantAvailability(RestaurantStatus.ACTIVE, null)).violation());

        RestaurantAvailability availability = new RestaurantAvailability(RestaurantStatus.ACTIVE, lunchHours);
        assertEquals(CatalogRuleViolation.INSTANT_REQUIRED,
                assertThrows(CatalogDomainException.class,
                        () -> availability.acceptsCheckout(MenuItemStatus.AVAILABLE, null)).violation());

        RestaurantAvailability paused = new RestaurantAvailability(RestaurantStatus.PAUSED, lunchHours);
        assertEquals(CatalogRuleViolation.INSTANT_REQUIRED,
                assertThrows(CatalogDomainException.class,
                        () -> paused.acceptsCheckout(MenuItemStatus.AVAILABLE, null)).violation());
        assertEquals(CatalogRuleViolation.INSTANT_REQUIRED,
                assertThrows(CatalogDomainException.class,
                        () -> availability.acceptsCheckout(MenuItemStatus.SOLD_OUT, null)).violation());
    }
}
