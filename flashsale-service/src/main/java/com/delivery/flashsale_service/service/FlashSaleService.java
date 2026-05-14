package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.*;
import com.delivery.flashsale_service.entity.*;
import com.delivery.flashsale_service.mapper.FlashSaleMapper;
import com.delivery.flashsale_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlashSaleService {
    private final FlashSaleCampaignRepository campaignRepo;
    private final FlashSaleItemRepository itemRepo;
    private final FlashSaleMapper mapper;

    // Admin methods
    @Transactional
    public FlashSaleCampaignDto createCampaign(CreateCampaignRequest req, Long adminId) {
        FlashSaleCampaign campaign = FlashSaleCampaign.builder()
                .name(req.getName())
                .isRecurring(req.getIsRecurring())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .adminId(adminId)
                .status(FlashSaleCampaign.CampaignStatus.UPCOMING)
                .build();
        return mapper.toDto(campaignRepo.save(campaign));
    }

    public List<FlashSaleCampaignDto> getAllCampaigns() {
        return campaignRepo.findAll().stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    public void updateCampaignStatus(Long id, String status) {
        FlashSaleCampaign campaign = campaignRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));
        campaign.setStatus(FlashSaleCampaign.CampaignStatus.valueOf(status));
        campaignRepo.save(campaign);
    }

    @Transactional
    public void approveItem(Long itemId) {
        FlashSaleItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        item.setStatus(FlashSaleItem.ItemStatus.APPROVED);
        itemRepo.save(item);
    }

    // Merchant methods
    @Transactional
    public FlashSaleItemDto registerItem(RegisterItemRequest req) {
        FlashSaleCampaign campaign = campaignRepo.findById(req.getCampaignId())
                .orElseThrow(() -> new RuntimeException("Campaign not found"));
        
        FlashSaleItem item = FlashSaleItem.builder()
                .campaign(campaign)
                .restaurantId(req.getRestaurantId())
                .menuItemId(req.getMenuItemId())
                .originalPrice(req.getOriginalPrice())
                .flashSalePrice(req.getFlashSalePrice())
                .stockQuantity(req.getStockQuantity())
                .soldQuantity(0)
                .status(FlashSaleItem.ItemStatus.PENDING)
                .build();
        return mapper.toDto(itemRepo.save(item));
    }

    public List<FlashSaleItemDto> getItemsByCampaign(Long campaignId) {
        return itemRepo.findByCampaignId(campaignId).stream()
                .map(mapper::toDto).collect(Collectors.toList());
    }
}
