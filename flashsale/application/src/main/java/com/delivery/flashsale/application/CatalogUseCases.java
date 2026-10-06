package com.delivery.flashsale.application;

import com.delivery.flashsale.application.api.CatalogPort;
import com.delivery.flashsale.domain.*;
import java.util.List;
import static com.delivery.flashsale.domain.FlashSaleCatalogPolicy.*;

public final class CatalogUseCases<C, I, S> {
    private static final int LIMIT = 100;
    private final CatalogPort<C, I, S> port;
    public CatalogUseCases(CatalogPort<C, I, S> port) { this.port = port; }
    public C createCampaign(FlashSaleInputs.Campaign request, Long adminId) {
        validateCampaign(request, adminId); return port.createCampaign(request, adminId);
    }
    public List<C> allCampaigns() { return port.allCampaigns(LIMIT); }
    public List<C> activeCampaigns() { return port.activeCampaigns(LIMIT); }
    public void updateStatus(Long id, S status) {
        validatePositiveId(id, "campaignId");
        requireCampaignStatus(status != null);
        port.updateStatus(id, status);
    }
    public void approveItem(Long id) { validatePositiveId(id, "itemId"); port.approveNondeletedItem(id); }
    public I registerItem(FlashSaleInputs.Item request) {
        validateItem(request); requireDiscount(request); return port.registerItem(request);
    }
    public List<I> allItems(Long id) { validatePositiveId(id, "campaignId"); return port.allItems(id, LIMIT); }
    public List<I> publicItems(Long id) {
        validatePositiveId(id, "campaignId");
        if (!publicCampaign(port.campaignActive(id))) return List.of();
        return port.approvedItems(id, LIMIT);
    }
}
