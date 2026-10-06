package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.*;
import com.delivery.flashsale_service.entity.*;
import com.delivery.flashsale_service.mapper.FlashSaleMapper;
import com.delivery.flashsale_service.repository.*;
import com.delivery.flashsale_service.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlashSaleCatalogPersistenceTest {
    final FlashSaleCampaignRepository campaigns = mock(FlashSaleCampaignRepository.class);
    final FlashSaleItemRepository items = mock(FlashSaleItemRepository.class);
    final FlashSaleMapper mapper = new FlashSaleMapper();
    final FlashSaleService service = new FlashSaleService(campaigns,items,mapper);

    @Test void campaignCreationPersistsAdminAndReturnsCompleteSnapshot() {
        var request = new CreateCampaignRequest(); request.setName("Lunch"); request.setIsRecurring(true);
        request.setStartTime(LocalTime.of(11,0)); request.setEndTime(LocalTime.of(13,0));
        when(campaigns.save(any())).thenAnswer(i -> { FlashSaleCampaign c = i.getArgument(0); c.setId(9L); return c; });
        var result = service.createCampaign(request,70L);
        assertThat(result.getId()).isEqualTo(9L); assertThat(result.getName()).isEqualTo("Lunch");
        assertThat(result.getAdminId()).isEqualTo(70L); assertThat(result.getIsRecurring()).isTrue();
        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(11,0)); assertThat(result.getEndTime()).isEqualTo(LocalTime.of(13,0));
        assertThat(result.getStatus()).isEqualTo("UPCOMING");
        verify(campaigns).save(argThat(c -> c.getAdminId().equals(70L) && c.getStatus()==FlashSaleCampaign.CampaignStatus.UPCOMING));
    }

    @Test void registrationPersistsPendingStockAndReturnsCanonicalPrices() {
        var campaign = FlashSaleCampaign.builder().id(9L).status(FlashSaleCampaign.CampaignStatus.ACTIVE).build();
        when(campaigns.findById(9L)).thenReturn(Optional.of(campaign));
        when(items.save(any())).thenAnswer(i -> { FlashSaleItem item = i.getArgument(0); item.setId(41L); return item; });
        var request = new RegisterItemRequest(); request.setCampaignId(9L); request.setRestaurantId(2L); request.setMenuItemId(3L);
        request.setOriginalPrice(new BigDecimal("100000")); request.setFlashSalePrice(new BigDecimal("80000")); request.setStockQuantity(10);
        var result = service.registerItem(request);
        assertThat(result.getId()).isEqualTo(41L); assertThat(result.getCampaignId()).isEqualTo(9L);
        assertThat(result.getRestaurantId()).isEqualTo(2L); assertThat(result.getMenuItemId()).isEqualTo(3L);
        assertThat(result.getOriginalPrice()).isEqualByComparingTo("100000"); assertThat(result.getFlashSalePrice()).isEqualByComparingTo("80000");
        assertThat(result.getStockQuantity()).isEqualTo(10); assertThat(result.getSoldQuantity()).isZero();
        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(items).save(argThat(i -> i.getCampaign()==campaign && i.getStatus()==FlashSaleItem.ItemStatus.PENDING && i.getSoldQuantity()==0));
    }

    @Test void statusUpdatePersistsAndMissingCampaignFailsWithoutWrite() {
        var campaign = FlashSaleCampaign.builder().id(9L).status(FlashSaleCampaign.CampaignStatus.UPCOMING).build();
        when(campaigns.findById(9L)).thenReturn(Optional.of(campaign));
        service.updateCampaignStatus(9L,FlashSaleCampaign.CampaignStatus.ACTIVE);
        assertThat(campaign.getStatus()).isEqualTo(FlashSaleCampaign.CampaignStatus.ACTIVE); verify(campaigns).save(campaign);
        assertThatThrownBy(() -> service.updateCampaignStatus(99L,FlashSaleCampaign.CampaignStatus.ACTIVE))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Campaign not found");
        verify(campaigns,times(1)).save(any());
    }

    @Test void mapperPreservesMissingStatusAndCampaignRatherThanInventingDefaults() {
        assertThat(mapper.toDto(new FlashSaleCampaign()).getStatus()).isNull();
        var dto = mapper.toDto(new FlashSaleItem());
        assertThat(dto.getStatus()).isNull(); assertThat(dto.getCampaignId()).isNull();
    }
}
