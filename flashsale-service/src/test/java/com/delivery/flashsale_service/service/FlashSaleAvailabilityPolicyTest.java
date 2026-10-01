package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import com.delivery.flashsale_service.entity.FlashSaleItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlashSaleAvailabilityPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"12:00", "13:00"})
    void campaignEndpointsAreInclusiveAndLastUnitsAreAvailable(String time) {
        FlashSaleItem item = availableItem();

        assertThatCode(() -> FlashSaleAvailabilityPolicy.requireAvailable(item, 9L, 2, LocalTime.parse(time)))
                .doesNotThrowAnyException();

        assertThat(item.getSoldQuantity()).isEqualTo(8);
        assertThat(item.getStatus()).isEqualTo(FlashSaleItem.ItemStatus.APPROVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"11:59:59.999999999", "13:00:00.000000001"})
    void oneNanosecondOutsideCampaignIsUnavailable(String time) {
        assertThatThrownBy(() -> FlashSaleAvailabilityPolicy.requireAvailable(
                availableItem(), 9L, 1, LocalTime.parse(time)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Flash sale campaign is not active");
    }

    @Test
    void rejectedDeletedItemDoesNotRequireCampaignOrStockData() {
        FlashSaleItem item = FlashSaleItem.builder().deletedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();

        assertThatThrownBy(() -> FlashSaleAvailabilityPolicy.requireAvailable(item, 9L, 1, LocalTime.NOON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Flash sale item is deleted");
    }

    @Test
    void insufficientStockDoesNotChangeCounters() {
        FlashSaleItem item = availableItem();

        assertThatThrownBy(() -> FlashSaleAvailabilityPolicy.requireAvailable(item, 9L, 3, LocalTime.NOON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Out of stock for flash sale item 41");

        assertThat(item.getSoldQuantity()).isEqualTo(8);
    }

    private static FlashSaleItem availableItem() {
        FlashSaleCampaign campaign = FlashSaleCampaign.builder()
                .status(FlashSaleCampaign.CampaignStatus.ACTIVE)
                .startTime(LocalTime.NOON).endTime(LocalTime.of(13, 0)).build();
        return FlashSaleItem.builder().id(41L).restaurantId(9L).campaign(campaign)
                .status(FlashSaleItem.ItemStatus.APPROVED).stockQuantity(10).soldQuantity(8).build();
    }
}
