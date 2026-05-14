package com.delivery.flashsale_service.repository;

import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashSaleCampaignRepository extends JpaRepository<FlashSaleCampaign, Long> {
}
