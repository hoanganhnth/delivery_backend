package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.entity.FlashSaleItem;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlashSaleCronServiceTest {

    @Test
    void recurringResetUsesOneBoundedDatabaseMutationInsteadOfLoadingEveryItem() {
        FlashSaleItemRepository repository = mock(FlashSaleItemRepository.class);
        when(repository.resetRecurringSoldQuantity(FlashSaleItem.ItemStatus.APPROVED)).thenReturn(250);

        new FlashSaleCronService(repository).resetRecurringCampaignStock();

        verify(repository).resetRecurringSoldQuantity(FlashSaleItem.ItemStatus.APPROVED);
    }
}
