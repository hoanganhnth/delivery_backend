package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.mapper.FlashSaleMapper;
import com.delivery.flashsale_service.repository.FlashSaleCampaignRepository;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalTime;
import com.delivery.flashsale_service.dto.RegisterItemRequest;
import com.delivery.flashsale_service.dto.CreateCampaignRequest;
import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import com.delivery.flashsale_service.entity.FlashSaleItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FlashSaleServiceQuerySafetyTest {

    @Mock FlashSaleCampaignRepository campaignRepository;
    @Mock FlashSaleItemRepository itemRepository;
    @Mock FlashSaleMapper mapper;

    @Test
    void compatibilityListsCapRepositoryQueriesAtOneHundred() {
        FlashSaleService service = new FlashSaleService(campaignRepository, itemRepository, mapper);
        Pageable firstHundred = Pageable.ofSize(100);
        when(campaignRepository.findAll(firstHundred)).thenReturn(new PageImpl<>(List.of()));
        when(itemRepository.findByCampaignId(9L, firstHundred)).thenReturn(List.of());
        when(campaignRepository.findByStatusOrderByStartTimeAsc(
                FlashSaleCampaign.CampaignStatus.ACTIVE, firstHundred)).thenReturn(List.of());

        assertEquals(List.of(), service.getAllCampaigns());
        assertEquals(List.of(), service.getActiveCampaigns());
        assertEquals(List.of(), service.getAllItemsByCampaign(9L));

        verify(campaignRepository).findAll(firstHundred);
        verify(campaignRepository).findByStatusOrderByStartTimeAsc(
                FlashSaleCampaign.CampaignStatus.ACTIVE, firstHundred);
        verify(itemRepository).findByCampaignId(9L, firstHundred);
    }

    @Test
    void publicItemsRequireActiveCampaignAndApprovedStatus() {
        FlashSaleService service = new FlashSaleService(campaignRepository, itemRepository, mapper);
        Pageable firstHundred = Pageable.ofSize(100);
        FlashSaleCampaign active = FlashSaleCampaign.builder()
                .id(9L).status(FlashSaleCampaign.CampaignStatus.ACTIVE).build();
        when(campaignRepository.findById(9L)).thenReturn(java.util.Optional.of(active));
        when(itemRepository.findByCampaignIdAndStatus(
                9L, FlashSaleItem.ItemStatus.APPROVED, firstHundred)).thenReturn(List.of());

        assertEquals(List.of(), service.getPublicItemsByCampaign(9L));

        verify(itemRepository).findByCampaignIdAndStatus(
                9L, FlashSaleItem.ItemStatus.APPROVED, firstHundred);
    }

    @Test
    void registerRejectsNonDiscountedPriceBeforeRepositoryCalls() {
        FlashSaleService service = new FlashSaleService(campaignRepository, itemRepository, mapper);
        RegisterItemRequest request = new RegisterItemRequest();
        request.setCampaignId(1L);
        request.setRestaurantId(2L);
        request.setMenuItemId(3L);
        request.setOriginalPrice(new BigDecimal("100000"));
        request.setFlashSalePrice(new BigDecimal("100000"));
        request.setStockQuantity(1);

        assertThrows(IllegalArgumentException.class, () -> service.registerItem(request));

        verifyNoInteractions(campaignRepository, itemRepository, mapper);
    }

    @Test
    void createCampaignRejectsInvertedTimeWindowBeforeRepositoryCalls() {
        FlashSaleService service = new FlashSaleService(campaignRepository, itemRepository, mapper);
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setName("Flash sale");
        request.setIsRecurring(true);
        request.setStartTime(LocalTime.of(18, 0));
        request.setEndTime(LocalTime.of(17, 0));

        assertThrows(IllegalArgumentException.class, () -> service.createCampaign(request, 9L));

        verifyNoInteractions(campaignRepository, itemRepository, mapper);
    }
}
