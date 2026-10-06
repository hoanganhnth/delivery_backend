package com.delivery.flashsale.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlashSaleAvailabilityPolicyTest {
    private static final LocalTime START = LocalTime.NOON;
    private static final LocalTime END = LocalTime.of(13, 0);

    @ParameterizedTest
    @MethodSource("availabilityCases")
    void preservesEveryDecisionAndFailurePriority(ItemView item, int quantity, LocalTime now, String error) {
        if (error == null) {
            assertThatCode(() -> FlashSaleAvailabilityPolicy.requireAvailable(item, 9L, quantity, now))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> FlashSaleAvailabilityPolicy.requireAvailable(item, 9L, quantity, now))
                    .isExactlyInstanceOf(IllegalArgumentException.class).hasMessage(error);
        }
    }

    static Stream<Arguments> availabilityCases() {
        List<Arguments> cases = new ArrayList<>();
        for (boolean deleted : List.of(false, true)) {
            for (Long owner : List.of(9L, 10L)) {
                for (boolean approved : List.of(false, true)) {
                    for (boolean active : List.of(false, true)) {
                        for (LocalTime now : List.of(START.minusNanos(1), START,
                                START.plusMinutes(30), END, END.plusNanos(1))) {
                            for (int quantity : List.of(1, 2, 3)) {
                                String error = null;
                                if (deleted) error = "Flash sale item is deleted";
                                else if (owner != 9L) error = "Flash sale item belongs to another restaurant";
                                else if (!approved) error = "Flash sale item is not approved";
                                else if (!active || now.isBefore(START) || now.isAfter(END))
                                    error = "Flash sale campaign is not active";
                                else if (quantity > 2) error = "Out of stock for flash sale item 41";
                                cases.add(Arguments.of(new ItemView(deleted, owner, approved, active,
                                        START, END, 10, 8, 41L), quantity, now, error));
                            }
                        }
                    }
                }
            }
        }
        return cases.stream();
    }

    @Test
    void deletionShortCircuitsMissingIdentityCampaignAndStock() {
        assertFailure(new ItemView(true, null, false, false, null, null, null, null, null),
                null, "Flash sale item is deleted");
    }

    @Test
    void ownershipShortCircuitsMissingCampaignAndStock() {
        assertFailure(new ItemView(false, 10L, false, false, null, null, null, null, null),
                null, "Flash sale item belongs to another restaurant");
    }

    @Test
    void approvalShortCircuitsMissingCampaignAndStock() {
        assertFailure(new ItemView(false, 9L, false, false, null, null, null, null, null),
                null, "Flash sale item is not approved");
    }

    @Test
    void inactiveCampaignShortCircuitsMissingTimeAndStock() {
        assertFailure(new ItemView(false, 9L, true, false, null, null, null, null, null),
                null, "Flash sale campaign is not active");
    }

    @Test
    void beforeStartShortCircuitsMissingEndAndStock() {
        assertFailure(new ItemView(false, 9L, true, true, START, null, null, null, null),
                START.minusNanos(1), "Flash sale campaign is not active");
    }

    @Test
    void afterEndShortCircuitsMissingStock() {
        assertFailure(new ItemView(false, 9L, true, true, START, END, null, null, null),
                END.plusNanos(1), "Flash sale campaign is not active");
    }

    @Test
    void preservesNullIdentityFailureRatherThanAddingValidation() {
        assertThatThrownBy(() -> FlashSaleAvailabilityPolicy.requireAvailable(
                new ItemView(false, null, true, true, START, END, 10, 8, 41L), 9L, 1, START))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void nullRequestedRestaurantIsAnOwnershipMismatch() {
        assertThatThrownBy(() -> FlashSaleAvailabilityPolicy.requireAvailable(
                new ItemView(false, 9L, true, true, START, END, 10, 8, 41L), null, 1, START))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Flash sale item belongs to another restaurant");
    }

    @Test
    void successfulAvailabilityDoesNotReadId() {
        FlashSaleAvailabilityPolicy.Item item = new ItemView(false, 9L, true, true, START, END, 10, 8, null) {
            @Override
            public Long id() { throw new AssertionError("ID is only needed for the stock error"); }
        };
        assertThatCode(() -> FlashSaleAvailabilityPolicy.requireAvailable(item, 9L, 2, START))
                .doesNotThrowAnyException();
    }

    @Test
    void preservesIntegerOverflowAndNullIdMessage() {
        assertFailure(new ItemView(false, 9L, true, true, START, END, Integer.MAX_VALUE, -1, null),
                START, "Out of stock for flash sale item null");
    }

    @Test
    void quantityValidationRemainsTheCallersResponsibility() {
        ItemView item = new ItemView(false, 9L, true, true, START, END, 0, 0, 41L);
        assertThatCode(() -> FlashSaleAvailabilityPolicy.requireAvailable(item, 9L, 0, START))
                .doesNotThrowAnyException();
        assertThatCode(() -> FlashSaleAvailabilityPolicy.requireAvailable(item, 9L, -1, START))
                .doesNotThrowAnyException();
        assertFailure(item, START, "Out of stock for flash sale item 41");
    }

    private static void assertFailure(ItemView item, LocalTime now, String error) {
        assertThatThrownBy(() -> FlashSaleAvailabilityPolicy.requireAvailable(item, 9L, 1, now))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessage(error);
    }

    private static class ItemView implements FlashSaleAvailabilityPolicy.Item {
        private final boolean deleted;
        private final Long restaurantId;
        private final boolean approved;
        private final boolean campaignActive;
        private final LocalTime campaignStartTime;
        private final LocalTime campaignEndTime;
        private final Integer stockQuantity;
        private final Integer soldQuantity;
        private final Long id;

        ItemView(boolean deleted, Long restaurantId, boolean approved, boolean campaignActive,
                 LocalTime campaignStartTime, LocalTime campaignEndTime,
                 Integer stockQuantity, Integer soldQuantity, Long id) {
            this.deleted = deleted;
            this.restaurantId = restaurantId;
            this.approved = approved;
            this.campaignActive = campaignActive;
            this.campaignStartTime = campaignStartTime;
            this.campaignEndTime = campaignEndTime;
            this.stockQuantity = stockQuantity;
            this.soldQuantity = soldQuantity;
            this.id = id;
        }

        public boolean deleted() { return deleted; }
        public Long restaurantId() { return restaurantId; }
        public boolean approved() { return approved; }
        public boolean campaignActive() { return campaignActive; }
        public LocalTime campaignStartTime() { return campaignStartTime; }
        public LocalTime campaignEndTime() { return campaignEndTime; }
        public Integer stockQuantity() { return stockQuantity; }
        public Integer soldQuantity() { return soldQuantity; }
        public Long id() { return id; }
    }
}
