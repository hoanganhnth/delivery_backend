package com.delivery.flashsale_service.repository;

import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface FlashSaleCampaignRepository extends JpaRepository<FlashSaleCampaign, Long> {
    List<FlashSaleCampaign> findByStatusOrderByStartTimeAsc(
            FlashSaleCampaign.CampaignStatus status, Pageable pageable);
}
