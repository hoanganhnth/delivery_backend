package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.*;
import com.delivery.flashsale_service.entity.*;
import com.delivery.flashsale_service.exception.ResourceNotFoundException;
import com.delivery.flashsale_service.mapper.FlashSaleMapper;
import com.delivery.flashsale_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlashSaleService {
    private static final int COMPATIBILITY_LIST_LIMIT = 100;

    private final FlashSaleCampaignRepository campaignRepo;
    private final FlashSaleItemRepository itemRepo;
    private final FlashSaleMapper mapper;

    // Admin methods
    @Transactional
    public FlashSaleCampaignDto createCampaign(CreateCampaignRequest req, Long adminId) {
        validateCreateCampaignRequest(req, adminId);
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
        return campaignRepo.findAll(PageRequest.of(0, COMPATIBILITY_LIST_LIMIT)).getContent().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FlashSaleCampaignDto> getActiveCampaigns() {
        return campaignRepo.findByStatusOrderByStartTimeAsc(
                        FlashSaleCampaign.CampaignStatus.ACTIVE,
                        PageRequest.of(0, COMPATIBILITY_LIST_LIMIT)).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateCampaignStatus(Long id, FlashSaleCampaign.CampaignStatus status) {
        validatePositiveId(id, "campaignId");
        if (status == null) {
            throw new IllegalArgumentException("Campaign status is required");
        }
        FlashSaleCampaign campaign = campaignRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        campaign.setStatus(status);
        campaignRepo.save(campaign);
    }

    @Transactional
    public void approveItem(Long itemId) {
        validatePositiveId(itemId, "itemId");
        FlashSaleItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        item.setStatus(FlashSaleItem.ItemStatus.APPROVED);
        itemRepo.save(item);
    }

    // Merchant methods
    @Transactional
    public FlashSaleItemDto registerItem(RegisterItemRequest req) {
        validateRegisterItemRequest(req);
        if (req.getFlashSalePrice().compareTo(req.getOriginalPrice()) >= 0) {
            throw new IllegalArgumentException("Flash sale price must be lower than original price");
        }
        FlashSaleCampaign campaign = campaignRepo.findById(req.getCampaignId())
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        
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

    @Transactional(readOnly = true)
    public List<FlashSaleItemDto> getAllItemsByCampaign(Long campaignId) {
        validatePositiveId(campaignId, "campaignId");
        return itemRepo.findByCampaignId(campaignId, PageRequest.of(0, COMPATIBILITY_LIST_LIMIT)).stream()
                .map(mapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FlashSaleItemDto> getPublicItemsByCampaign(Long campaignId) {
        validatePositiveId(campaignId, "campaignId");
        FlashSaleCampaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        if (campaign.getStatus() != FlashSaleCampaign.CampaignStatus.ACTIVE) {
            return List.of();
        }
        return itemRepo.findByCampaignIdAndStatus(
                        campaignId,
                        FlashSaleItem.ItemStatus.APPROVED,
                        PageRequest.of(0, COMPATIBILITY_LIST_LIMIT)).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    private void validateCreateCampaignRequest(CreateCampaignRequest req, Long adminId) {
        if (req == null) {
            throw new IllegalArgumentException("Campaign request is required");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("Campaign name is required");
        }
        if (req.getIsRecurring() == null) {
            throw new IllegalArgumentException("Campaign recurrence flag is required");
        }
        if (req.getStartTime() == null || req.getEndTime() == null) {
            throw new IllegalArgumentException("Campaign time window is required");
        }
        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new IllegalArgumentException("Campaign startTime must be before endTime");
        }
        validatePositiveId(adminId, "adminId");
    }

    private void validateRegisterItemRequest(RegisterItemRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Flash sale item request is required");
        }
        validatePositiveId(req.getCampaignId(), "campaignId");
        validatePositiveId(req.getRestaurantId(), "restaurantId");
        validatePositiveId(req.getMenuItemId(), "menuItemId");
        validatePositiveId(req.getStockQuantity() == null ? null : req.getStockQuantity().longValue(), "stockQuantity");
        if (req.getOriginalPrice() == null || req.getOriginalPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Original price must be positive");
        }
        if (req.getFlashSalePrice() == null || req.getFlashSalePrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Flash sale price must be positive");
        }
    }

    private void validatePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
