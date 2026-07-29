package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.entity.FlashSaleItem;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleCronService {
    private final FlashSaleItemRepository itemRepo;

    @Scheduled(cron = "0 0 0 * * ?") // Runs at midnight every day
    @Transactional
    public void resetRecurringCampaignStock() {
        log.info("Running daily cron job to reset recurring flash sale stock...");
        int updated = itemRepo.resetRecurringSoldQuantity(FlashSaleItem.ItemStatus.APPROVED);
        log.info("Reset stock for {} recurring flash sale items.", updated);
    }
}
