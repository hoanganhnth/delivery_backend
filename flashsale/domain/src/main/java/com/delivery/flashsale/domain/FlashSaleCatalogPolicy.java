package com.delivery.flashsale.domain;

public final class FlashSaleCatalogPolicy {
    private FlashSaleCatalogPolicy() { }
    public static void validateCampaign(FlashSaleInputs.Campaign req, Long adminId) {
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

    public static void validateItem(FlashSaleInputs.Item req) {
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

    public static void requireDiscount(FlashSaleInputs.Item req) {
        if (req.getFlashSalePrice().compareTo(req.getOriginalPrice()) >= 0)
            throw new IllegalArgumentException("Flash sale price must be lower than original price");
    }

    public static String initialCampaignStatus() { return "UPCOMING"; }
    public static String initialItemStatus() { return "PENDING"; }
    public static String approvedItemStatus() { return "APPROVED"; }
    public static boolean approvable(boolean deleted) { return !deleted; }

    public static boolean publicCampaign(boolean active) { return active; }

    public static void requireCampaignStatus(boolean present) {
        if (!present) throw new IllegalArgumentException("Campaign status is required");
    }

    public static void validatePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
