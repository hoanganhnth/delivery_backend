package com.delivery.flashsale.application;

import com.delivery.flashsale.application.api.CatalogPort;
import com.delivery.flashsale.application.api.OwnershipPort;
import com.delivery.flashsale.domain.FlashSaleInputs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogUseCasesTest {
    private final CatalogPort<String, String, String> port = mock(CatalogPort.class);
    private final CatalogUseCases<String, String, String> useCases = new CatalogUseCases<>(port);

    @Test
    void validCommandsAndBoundedQueriesDelegateWithoutChangingResults() {
        FlashSaleInputs.Campaign campaign = mock(FlashSaleInputs.Campaign.class);
        when(campaign.getName()).thenReturn("Lunch");
        when(campaign.getIsRecurring()).thenReturn(true);
        when(campaign.getStartTime()).thenReturn(LocalTime.NOON);
        when(campaign.getEndTime()).thenReturn(LocalTime.of(13, 0));
        when(port.createCampaign(campaign, 7L)).thenReturn("campaign");
        assertThat(useCases.createCampaign(campaign, 7L)).isEqualTo("campaign");

        FlashSaleInputs.Item item = validItem();
        when(port.registerItem(item)).thenReturn("item");
        assertThat(useCases.registerItem(item)).isEqualTo("item");
        useCases.updateStatus(1L, "ACTIVE");
        useCases.approveItem(2L);
        verify(port).updateStatus(1L, "ACTIVE");
        verify(port).approveNondeletedItem(2L);

        List<String> rows = List.of("row");
        when(port.allCampaigns(100)).thenReturn(rows);
        when(port.activeCampaigns(100)).thenReturn(rows);
        when(port.allItems(1L, 100)).thenReturn(rows);
        assertThat(useCases.allCampaigns()).isSameAs(rows);
        assertThat(useCases.activeCampaigns()).isSameAs(rows);
        assertThat(useCases.allItems(1L)).isSameAs(rows);
    }

    @Test
    void invalidCommandsFailBeforePersistenceAndKeepValidationOrder() {
        assertThatThrownBy(() -> useCases.createCampaign(null, 0L)).hasMessage("Campaign request is required");
        assertThatThrownBy(() -> useCases.registerItem(null)).hasMessage("Flash sale item request is required");
        FlashSaleInputs.Item item = validItem();
        when(item.getFlashSalePrice()).thenReturn(new BigDecimal("100"));
        assertThatThrownBy(() -> useCases.registerItem(item)).hasMessage("Flash sale price must be lower than original price");
        assertThatThrownBy(() -> useCases.updateStatus(0L, null)).hasMessage("campaignId must be positive");
        assertThatThrownBy(() -> useCases.updateStatus(1L, null)).hasMessage("Campaign status is required");
        assertThatThrownBy(() -> useCases.approveItem(null)).hasMessage("itemId must be positive");
        assertThatThrownBy(() -> useCases.allItems(0L)).hasMessage("campaignId must be positive");
        assertThatThrownBy(() -> useCases.publicItems(null)).hasMessage("campaignId must be positive");
        verifyNoInteractions(port);
    }

    @Test
    void publicCatalogStillUsesStatusOnlyNotTimeOrCapacityEligibility() {
        when(port.campaignActive(1L)).thenReturn(false, true);
        List<String> rows = List.of("approved but exhausted or outside window");
        when(port.approvedItems(1L, 100)).thenReturn(rows);
        assertThat(useCases.publicItems(1L)).isEmpty();
        verify(port, never()).approvedItems(anyLong(), anyInt());
        assertThat(useCases.publicItems(1L)).isSameAs(rows);
        verify(port).approvedItems(1L, 100);
    }

    @Test
    void ownershipPrecedesRegistrationAndFailuresPropagateUnchanged() {
        OwnershipPort ownership = mock(OwnershipPort.class);
        Supplier<String> registration = mock(Supplier.class);
        var merchant = new MerchantRegistrationUseCase(ownership);
        when(registration.get()).thenReturn("registered");
        assertThat(merchant.register(1L, 2L, 3L, registration)).isEqualTo("registered");
        var order = inOrder(ownership, registration);
        order.verify(ownership).requireOwnedBy(1L, 2L, 3L);
        order.verify(registration).get();
        var denied = new IllegalArgumentException("not owned");
        doThrow(denied).when(ownership).requireOwnedBy(1L, 2L, 3L);
        assertThatThrownBy(() -> merchant.register(1L, 2L, 3L, registration)).isSameAs(denied);
        verify(registration, times(1)).get();
    }

    private FlashSaleInputs.Item validItem() {
        FlashSaleInputs.Item item = mock(FlashSaleInputs.Item.class);
        when(item.getCampaignId()).thenReturn(1L);
        when(item.getRestaurantId()).thenReturn(2L);
        when(item.getMenuItemId()).thenReturn(3L);
        when(item.getStockQuantity()).thenReturn(4);
        when(item.getOriginalPrice()).thenReturn(new BigDecimal("100"));
        when(item.getFlashSalePrice()).thenReturn(new BigDecimal("50"));
        return item;
    }
}
