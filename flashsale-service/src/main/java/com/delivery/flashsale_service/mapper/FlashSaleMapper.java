package com.delivery.flashsale_service.mapper;

import com.delivery.flashsale_service.dto.*;
import com.delivery.flashsale_service.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FlashSaleMapper {
    FlashSaleCampaignDto toDto(FlashSaleCampaign campaign);
    
    @Mapping(target = "campaignId", source = "campaign.id")
    FlashSaleItemDto toDto(FlashSaleItem item);
}
