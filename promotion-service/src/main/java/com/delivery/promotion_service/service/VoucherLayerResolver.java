package com.delivery.promotion_service.service;

import com.delivery.promotion_service.entity.Voucher;

/**
 * Resolves the compatibility-era Voucher shape into the explicit stacking
 * layer. The database migration will add a persisted layer later; deriving it
 * here keeps old rows readable during the expand/contract rollout.
 */
public final class VoucherLayerResolver {
    private VoucherLayerResolver() {
    }

    public static VoucherLayer resolve(Voucher voucher) {
        if (voucher == null || voucher.getRewardType() == null) {
            throw new IllegalArgumentException("Voucher reward type is required");
        }
        if (voucher.getLayerCode() != null && !voucher.getLayerCode().isBlank()) {
            try {
                return VoucherLayer.valueOf(voucher.getLayerCode().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Voucher layer is invalid");
            }
        }
        if (voucher.getRewardType() == Voucher.RewardType.FREESHIP) {
            return VoucherLayer.FREESHIP;
        }
        if (voucher.getCreatorType() == Voucher.CreatorType.MERCHANT
                || voucher.getCreatorType() == Voucher.CreatorType.SHOP) {
            return VoucherLayer.SHOP_DISCOUNT;
        }
        return VoucherLayer.PLATFORM_DISCOUNT;
    }
}
