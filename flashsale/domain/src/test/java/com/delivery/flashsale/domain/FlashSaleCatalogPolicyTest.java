package com.delivery.flashsale.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.delivery.flashsale.domain.FlashSaleCatalogPolicy.*;

class FlashSaleCatalogPolicyTest {
    @Test void campaignValidationPreservesFieldOrderAndServiceCompatibility() {
        assertThatThrownBy(() -> validateCampaign(null, null)).hasMessage("Campaign request is required");
        var input = mock(FlashSaleInputs.Campaign.class);
        when(input.getIsRecurring()).thenReturn(null);
        for (String name : new String[]{null, " ", ""}) {
            when(input.getName()).thenReturn(name);
            assertThatThrownBy(() -> validateCampaign(input, null)).hasMessage("Campaign name is required");
        }
        when(input.getName()).thenReturn("x".repeat(256)); // DTO size constraint is not service validation.
        assertThatThrownBy(() -> validateCampaign(input, null)).hasMessage("Campaign recurrence flag is required");
        when(input.getIsRecurring()).thenReturn(false);
        assertThatThrownBy(() -> validateCampaign(input, null)).hasMessage("Campaign time window is required");
        when(input.getStartTime()).thenReturn(LocalTime.NOON);
        assertThatThrownBy(() -> validateCampaign(input, null)).hasMessage("Campaign time window is required");
        for (LocalTime end : new LocalTime[]{LocalTime.NOON, LocalTime.MIN}) {
            when(input.getEndTime()).thenReturn(end);
            assertThatThrownBy(() -> validateCampaign(input, null)).hasMessage("Campaign startTime must be before endTime");
        }
        when(input.getEndTime()).thenReturn(LocalTime.MAX);
        assertThatThrownBy(() -> validateCampaign(input, null)).hasMessage("adminId must be positive");
        validateCampaign(input, 1L);
    }
    @Test void itemValidationAndDiscountRetainOrderedFailures() {
        assertThatThrownBy(() -> validateItem(null)).hasMessage("Flash sale item request is required");
        var input = mock(FlashSaleInputs.Item.class);
        assertThatThrownBy(() -> validateItem(input)).hasMessage("campaignId must be positive");
        when(input.getCampaignId()).thenReturn(1L);
        assertThatThrownBy(() -> validateItem(input)).hasMessage("restaurantId must be positive");
        when(input.getRestaurantId()).thenReturn(2L);
        assertThatThrownBy(() -> validateItem(input)).hasMessage("menuItemId must be positive");
        when(input.getMenuItemId()).thenReturn(3L);
        when(input.getStockQuantity()).thenReturn(null);
        assertThatThrownBy(() -> validateItem(input)).hasMessage("stockQuantity must be positive");
        when(input.getStockQuantity()).thenReturn(0);
        assertThatThrownBy(() -> validateItem(input)).hasMessage("stockQuantity must be positive");
        when(input.getStockQuantity()).thenReturn(1);
        for (BigDecimal price : new BigDecimal[]{null, BigDecimal.ZERO, BigDecimal.ONE.negate()}) {
            when(input.getOriginalPrice()).thenReturn(price);
            assertThatThrownBy(() -> validateItem(input)).hasMessage("Original price must be positive");
        }
        when(input.getOriginalPrice()).thenReturn(BigDecimal.TEN);
        for (BigDecimal price : new BigDecimal[]{null, BigDecimal.ZERO, BigDecimal.ONE.negate()}) {
            when(input.getFlashSalePrice()).thenReturn(price);
            assertThatThrownBy(() -> validateItem(input)).hasMessage("Flash sale price must be positive");
        }
        when(input.getFlashSalePrice()).thenReturn(BigDecimal.ONE); validateItem(input); requireDiscount(input);
        for (BigDecimal price : new BigDecimal[]{BigDecimal.TEN, new BigDecimal("11")}) {
            when(input.getFlashSalePrice()).thenReturn(price);
            assertThatThrownBy(() -> requireDiscount(input)).hasMessage("Flash sale price must be lower than original price");
        }
    }
    @Test void lifecycleAndPositiveIdentifiers() {
        for (Long id : new Long[]{null, 0L, -1L}) assertThatThrownBy(() -> validatePositiveId(id,"id")).hasMessage("id must be positive");
        validatePositiveId(1L,"id");
        assertThat(initialCampaignStatus()).isEqualTo("UPCOMING");
        assertThat(initialItemStatus()).isEqualTo("PENDING");
        assertThat(approvedItemStatus()).isEqualTo("APPROVED");
        assertThat(approvable(false)).isTrue(); assertThat(approvable(true)).isFalse();
        assertThat(publicCampaign(true)).isTrue(); assertThat(publicCampaign(false)).isFalse();
        requireCampaignStatus(true);
        assertThatThrownBy(() -> requireCampaignStatus(false)).hasMessage("Campaign status is required");
    }
}
