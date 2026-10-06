package com.delivery.promotion.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

/** Creation decisions only; ownership verification and persistence remain with the caller. */
public final class CampaignPolicy {
    private CampaignPolicy() {}

    public record Campaign(String getCode,
            String getName,
            Voucher.CreatorType getCreatorType,
            Voucher.RewardType getRewardType,
            Voucher.ScopeType getScopeType,
            BigDecimal getDiscountValue,
            BigDecimal getMaxDiscountValue,
            Integer getTotalQuantity,
            Integer getUsageLimitPerUser,
            LocalDateTime getStartTime,
            LocalDateTime getEndTime,
            BigDecimal getMinOrderValue,
            Long getScopeRefId,
            Long getRestaurantId,
            Long getOwnerPrincipalId,
            String getLayerCode) {}
    public record Outcome(String layer, String fundingSource, String approvalStatus,
                          boolean active, Long restaurantId, IllegalArgumentException failure) {}

    public static Outcome create(Campaign request) {
        try {
            validate(request);
        } catch (IllegalArgumentException failure) {
            return new Outcome(null, null, null, false, null, failure);
        }
        boolean shop = request.getCreatorType() == Voucher.CreatorType.SHOP;
        String layer = request.getLayerCode();
        if (layer == null || layer.isBlank()) {
            layer = request.getRewardType() == Voucher.RewardType.FREESHIP
                    ? VoucherLayer.FREESHIP.name()
                    : shop ? VoucherLayer.SHOP_DISCOUNT.name() : VoucherLayer.PLATFORM_DISCOUNT.name();
        }
        // Historical creation validates trimmed layer text but stores the untrimmed uppercase value.
        return new Outcome(layer.toUpperCase(Locale.ROOT), shop ? "SHOP" : "PLATFORM",
                shop ? "PENDING" : "APPROVED", !shop,
                shop ? request.getRestaurantId() : request.getScopeRefId(), null);
    }

    private static void validate(Campaign request) {
        if (request == null) {
            throw new IllegalArgumentException("Create voucher request is required");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Voucher code is required");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Voucher name is required");
        }
        if (request.getCreatorType() == null) {
            throw new IllegalArgumentException("Voucher creatorType is required");
        }
        if (request.getRewardType() == null) {
            throw new IllegalArgumentException("Voucher rewardType is required");
        }
        if (request.getScopeType() == null) {
            throw new IllegalArgumentException("Voucher scopeType is required");
        }
        if (request.getDiscountValue() == null || request.getDiscountValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Voucher discountValue must be non-negative");
        }
        if (request.getMaxDiscountValue() != null
                && request.getMaxDiscountValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Voucher maxDiscountValue must be non-negative");
        }
        if (request.getTotalQuantity() == null || request.getTotalQuantity() < 1) {
            throw new IllegalArgumentException("Voucher totalQuantity must be positive");
        }
        if (request.getUsageLimitPerUser() == null || request.getUsageLimitPerUser() < 1) {
            throw new IllegalArgumentException("Voucher usageLimitPerUser must be positive");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new IllegalArgumentException("Voucher time window is required");
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new IllegalArgumentException("Voucher startTime must be before endTime");
        }
        if (request.getMinOrderValue() == null || request.getMinOrderValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Voucher minOrderValue must be non-negative");
        }
        if (request.getCreatorType() != Voucher.CreatorType.PLATFORM
                && request.getCreatorType() != Voucher.CreatorType.SHOP) {
            throw new IllegalArgumentException("Voucher campaigns must be platform or shop owned");
        }
        if (request.getScopeType() != Voucher.ScopeType.ALL
                && request.getScopeType() != Voucher.ScopeType.SHOP) {
            throw new IllegalArgumentException("Voucher scope must be ALL or SHOP");
        }
        if (request.getScopeType() == Voucher.ScopeType.SHOP
                && (request.getScopeRefId() == null || request.getScopeRefId() <= 0)) {
            throw new IllegalArgumentException("Voucher scopeRefId must identify a restaurant");
        }
        if (request.getScopeType() == Voucher.ScopeType.ALL && request.getScopeRefId() != null) {
            throw new IllegalArgumentException("Platform voucher must not carry scopeRefId");
        }
        if (request.getCreatorType() == Voucher.CreatorType.SHOP) {
            if (request.getRestaurantId() == null || request.getRestaurantId() <= 0
                    || request.getOwnerPrincipalId() == null || request.getOwnerPrincipalId() <= 0) {
                throw new IllegalArgumentException("Shop voucher requires restaurantId and ownerPrincipalId");
            }
            if (request.getRewardType() == Voucher.RewardType.FREESHIP) {
                throw new IllegalArgumentException("Shop vouchers cannot fund freeship");
            }
            if (request.getScopeType() != Voucher.ScopeType.SHOP
                    || !request.getRestaurantId().equals(request.getScopeRefId())) {
                throw new IllegalArgumentException("Shop voucher must target its restaurant");
            }
        } else if (request.getRewardType() == Voucher.RewardType.FREESHIP
                && request.getScopeType() != Voucher.ScopeType.ALL) {
            throw new IllegalArgumentException("Freeship voucher must be platform-wide");
        }

        if (request.getLayerCode() != null && !request.getLayerCode().isBlank()) {
            VoucherLayer requestedLayer;
            try {
                requestedLayer = VoucherLayer.valueOf(request.getLayerCode().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalidLayer) {
                throw new IllegalArgumentException("Voucher layer is invalid", invalidLayer);
            }
            if (request.getRewardType() == Voucher.RewardType.FREESHIP
                    && requestedLayer != VoucherLayer.FREESHIP) {
                throw new IllegalArgumentException("Freeship reward requires the FREESHIP layer");
            }
            if (request.getRewardType() != Voucher.RewardType.FREESHIP
                    && requestedLayer == VoucherLayer.FREESHIP) {
                throw new IllegalArgumentException("FREESHIP layer requires a freeship reward");
            }
            if (request.getCreatorType() == Voucher.CreatorType.SHOP
                    && requestedLayer != VoucherLayer.SHOP_DISCOUNT) {
                throw new IllegalArgumentException("Shop voucher requires the SHOP_DISCOUNT layer");
            }
            if (request.getCreatorType() == Voucher.CreatorType.PLATFORM
                    && requestedLayer == VoucherLayer.SHOP_DISCOUNT) {
                throw new IllegalArgumentException("Platform voucher cannot use the SHOP_DISCOUNT layer");
            }
        }
    }

}
