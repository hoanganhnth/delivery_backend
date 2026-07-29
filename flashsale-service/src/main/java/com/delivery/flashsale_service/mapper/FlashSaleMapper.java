package com.delivery.flashsale_service.mapper;

import com.delivery.flashsale_service.dto.*;
import com.delivery.flashsale_service.entity.*;
import org.springframework.stereotype.Component;

@Component
public class FlashSaleMapper {
    public FlashSaleCampaignDto toDto(FlashSaleCampaign campaign) {
        FlashSaleCampaignDto dto = new FlashSaleCampaignDto();
        dto.setId(campaign.getId());
        dto.setName(campaign.getName());
        dto.setIsRecurring(campaign.getIsRecurring());
        dto.setStartTime(campaign.getStartTime());
        dto.setEndTime(campaign.getEndTime());
        dto.setStatus(campaign.getStatus() == null ? null : campaign.getStatus().name());
        dto.setAdminId(campaign.getAdminId());
        return dto;
    }

    public FlashSaleItemDto toDto(FlashSaleItem item) {
        FlashSaleItemDto dto = new FlashSaleItemDto();
        dto.setId(item.getId());
        dto.setCampaignId(item.getCampaign() == null ? null : item.getCampaign().getId());
        dto.setRestaurantId(item.getRestaurantId());
        dto.setMenuItemId(item.getMenuItemId());
        dto.setOriginalPrice(item.getOriginalPrice());
        dto.setFlashSalePrice(item.getFlashSalePrice());
        dto.setStockQuantity(item.getStockQuantity());
        dto.setSoldQuantity(item.getSoldQuantity());
        dto.setStatus(item.getStatus() == null ? null : item.getStatus().name());
        return dto;
    }
}
