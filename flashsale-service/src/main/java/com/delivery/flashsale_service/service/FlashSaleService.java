package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.*;
import com.delivery.flashsale.domain.FlashSaleInputs;
import com.delivery.flashsale.application.CatalogUseCases;
import com.delivery.flashsale.application.api.CatalogPort;
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
    private final FlashSaleCampaignRepository campaignRepo;
    private final FlashSaleItemRepository itemRepo;
    private final FlashSaleMapper mapper;

    private CatalogUseCases<FlashSaleCampaignDto, FlashSaleItemDto, FlashSaleCampaign.CampaignStatus> useCases() {
        return new CatalogUseCases<>(new PersistenceAdapter());
    }
    @Transactional
    public FlashSaleCampaignDto createCampaign(CreateCampaignRequest req, Long adminId) { return useCases().createCampaign(req, adminId); }
    public List<FlashSaleCampaignDto> getAllCampaigns() { return useCases().allCampaigns(); }
    @Transactional(readOnly = true)
    public List<FlashSaleCampaignDto> getActiveCampaigns() { return useCases().activeCampaigns(); }
    @Transactional
    public void updateCampaignStatus(Long id, FlashSaleCampaign.CampaignStatus status) { useCases().updateStatus(id, status); }
    @Transactional
    public void approveItem(Long id) { useCases().approveItem(id); }
    @Transactional
    public FlashSaleItemDto registerItem(RegisterItemRequest req) { return useCases().registerItem(req); }
    @Transactional(readOnly = true)
    public List<FlashSaleItemDto> getAllItemsByCampaign(Long id) { return useCases().allItems(id); }
    @Transactional(readOnly = true)
    public List<FlashSaleItemDto> getPublicItemsByCampaign(Long id) { return useCases().publicItems(id); }

    private final class PersistenceAdapter implements CatalogPort<FlashSaleCampaignDto, FlashSaleItemDto, FlashSaleCampaign.CampaignStatus> {
        // Admin methods
        public FlashSaleCampaignDto createCampaign(FlashSaleInputs.Campaign req, Long adminId) {
            FlashSaleCampaign campaign = FlashSaleCampaign.builder()
                    .name(req.getName())
                    .isRecurring(req.getIsRecurring())
                    .startTime(req.getStartTime())
                    .endTime(req.getEndTime())
                    .adminId(adminId)
                    .status(FlashSaleCampaign.CampaignStatus.valueOf(com.delivery.flashsale.domain.FlashSaleCatalogPolicy.initialCampaignStatus()))
                    .build();
            return mapper.toDto(campaignRepo.save(campaign));
        }
    
        public List<FlashSaleCampaignDto> allCampaigns(int limit) {
            return campaignRepo.findAll(PageRequest.of(0, limit)).getContent().stream()
                    .map(mapper::toDto)
                    .collect(Collectors.toList());
        }
    
        public List<FlashSaleCampaignDto> activeCampaigns(int limit) {
            return campaignRepo.findByStatusOrderByStartTimeAsc(
                            FlashSaleCampaign.CampaignStatus.ACTIVE,
                            PageRequest.of(0, limit)).stream()
                    .map(mapper::toDto)
                    .collect(Collectors.toList());
        }
    
        public void updateStatus(Long id, FlashSaleCampaign.CampaignStatus status) {
            FlashSaleCampaign campaign = campaignRepo.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
            campaign.setStatus(status);
            campaignRepo.save(campaign);
        }
    
        public void approveNondeletedItem(Long itemId) {
            FlashSaleItem item = itemRepo.findById(itemId)
                    .filter(candidate -> com.delivery.flashsale.domain.FlashSaleCatalogPolicy.approvable(candidate.getDeletedAt() != null))
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
            item.setStatus(FlashSaleItem.ItemStatus.valueOf(com.delivery.flashsale.domain.FlashSaleCatalogPolicy.approvedItemStatus()));
            itemRepo.save(item);
        }
    
        // Merchant methods
        public FlashSaleItemDto registerItem(FlashSaleInputs.Item req) {
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
                    .status(FlashSaleItem.ItemStatus.valueOf(com.delivery.flashsale.domain.FlashSaleCatalogPolicy.initialItemStatus()))
                    .build();
            return mapper.toDto(itemRepo.save(item));
        }
    
        public List<FlashSaleItemDto> allItems(Long campaignId, int limit) {
            return itemRepo.findByCampaignId(campaignId, PageRequest.of(0, limit)).stream()
                    .map(mapper::toDto).collect(Collectors.toList());
        }
    
        public boolean campaignActive(Long campaignId) {
            FlashSaleCampaign campaign = campaignRepo.findById(campaignId)
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
            return campaign.getStatus() == FlashSaleCampaign.CampaignStatus.ACTIVE;
        }
    
        public List<FlashSaleItemDto> approvedItems(Long campaignId, int limit) {
            return itemRepo.findByCampaignIdAndStatus(campaignId, FlashSaleItem.ItemStatus.APPROVED,
                    PageRequest.of(0, limit)).stream().map(mapper::toDto).collect(Collectors.toList());
        }
    }
}
