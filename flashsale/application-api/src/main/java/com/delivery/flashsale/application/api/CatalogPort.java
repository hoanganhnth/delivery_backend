package com.delivery.flashsale.application.api;

import com.delivery.flashsale.domain.FlashSaleInputs;
import java.util.List;

/** Host persistence/mapping operations; invoked inside the existing host transaction. */
public interface CatalogPort<C, I, S> {
    C createCampaign(FlashSaleInputs.Campaign request, Long adminId);
    List<C> allCampaigns(int limit);
    List<C> activeCampaigns(int limit);
    void updateStatus(Long id, S status);
    void approveNondeletedItem(Long id);
    I registerItem(FlashSaleInputs.Item request);
    List<I> allItems(Long campaignId, int limit);
    boolean campaignActive(Long id);
    List<I> approvedItems(Long campaignId, int limit);
}
