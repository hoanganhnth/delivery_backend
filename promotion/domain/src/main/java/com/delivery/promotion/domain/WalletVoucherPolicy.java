package com.delivery.promotion.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Wallet collection and legacy single-voucher eligibility, without persistence or a clock. */
public final class WalletVoucherPolicy {
    private WalletVoucherPolicy() {}

    public static boolean isApproved(Voucher voucher) {
        return voucher != null && (voucher.getApprovalStatus() == null
                || "APPROVED".equalsIgnoreCase(voucher.getApprovalStatus()));
    }

    public static boolean isCheckoutEligible(Voucher voucher) {
        if (voucher == null || voucher.getDeletedAt() != null
                || (voucher.getCreatorType() != Voucher.CreatorType.PLATFORM
                && voucher.getCreatorType() != Voucher.CreatorType.SHOP)) return false;
        if (voucher.getScopeType() != Voucher.ScopeType.ALL
                && voucher.getScopeType() != Voucher.ScopeType.SHOP) return false;
        if ((voucher.getScopeType() == Voucher.ScopeType.ALL && voucher.getScopeRefId() != null)
                || (voucher.getScopeType() == Voucher.ScopeType.SHOP && voucher.getScopeRefId() == null)) return false;
        if (voucher.getEndTime() == null) return false;
        if (voucher.getDiscountValue() == null || voucher.getDiscountValue().signum() < 0) return false;
        if (voucher.getMinOrderValue() != null && voucher.getMinOrderValue().signum() < 0) return false;
        if (voucher.getMaxDiscountValue() != null && voucher.getMaxDiscountValue().signum() < 0) return false;
        if (voucher.getTotalQuantity() == null || voucher.getTotalQuantity() < 1
                || voucher.getUsedQuantity() == null || voucher.getUsedQuantity() < 0) return false;
        try {
            VoucherLayer layer = VoucherLayerResolver.resolve(voucher);
            if (voucher.getCreatorType() == Voucher.CreatorType.SHOP
                    && (layer != VoucherLayer.SHOP_DISCOUNT
                    || voucher.getScopeType() != Voucher.ScopeType.SHOP)) return false;
            if (voucher.getCreatorType() == Voucher.CreatorType.PLATFORM
                    && layer == VoucherLayer.SHOP_DISCOUNT) return false;
            if (layer == VoucherLayer.FREESHIP) {
                return voucher.getRewardType() == Voucher.RewardType.FREESHIP
                        && voucher.getScopeType() == Voucher.ScopeType.ALL;
            }
            return voucher.getRewardType() != Voucher.RewardType.FREESHIP;
        } catch (IllegalArgumentException invalidLayer) {
            return false;
        }
    }

    public static void requireCollectable(Voucher voucher, LocalDateTime now) {
        String failure = collectionUnavailableReason(voucher, now);
        if (failure != null) throw new IllegalArgumentException(failure);
    }

    public static String collectionUnavailableReason(Voucher voucher, LocalDateTime now) {
        if (!isCheckoutEligible(voucher)) {
            return "Voucher is not checkout-eligible";
        }
        if (!isApproved(voucher) || !Boolean.TRUE.equals(voucher.getActive()) || voucher.getEndTime().isBefore(now)) {
            return "Voucher is expired or inactive";
        }
        if (voucher.getStartTime() != null && now.isBefore(voucher.getStartTime())) {
            return "Voucher is not active yet";
        }
        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            return "Voucher is out of stock";
        }
        return null;
    }

    public static String reservationUnavailableReason(Voucher voucher, Long restaurantId,
                                               BigDecimal subtotal, LocalDateTime now) {
        if (!isCheckoutEligible(voucher)) return "Voucher is not checkout-eligible";
        if (!isApproved(voucher)) return "Voucher is not approved";
        if (!Boolean.TRUE.equals(voucher.getActive())) return "Voucher is inactive";
        if (voucher.getStartTime() != null && now.isBefore(voucher.getStartTime())) return "Voucher is not active yet";
        if (voucher.getEndTime() == null || !now.isBefore(voucher.getEndTime())) return "Voucher expired";
        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) return "Out of stock";
        BigDecimal minimum = voucher.getMinOrderValue() == null ? BigDecimal.ZERO : voucher.getMinOrderValue();
        if (subtotal.compareTo(minimum) < 0) return "Need " + minimum.subtract(subtotal) + " more to use";
        if (voucher.getScopeType() == Voucher.ScopeType.SHOP
                && (voucher.getScopeRefId() == null || !voucher.getScopeRefId().equals(restaurantId))) {
            return "Not applicable for this shop";
        }
        if (voucher.getScopeType() == Voucher.ScopeType.CATEGORY) {
            return "Legacy CATEGORY voucher is not checkout-eligible";
        }
        try {
            VoucherLayer layer = VoucherLayerResolver.resolve(voucher);
            if (layer == VoucherLayer.FREESHIP && voucher.getRewardType() != Voucher.RewardType.FREESHIP) {
                return "Freeship layer requires a freeship reward";
            }
            if (layer != VoucherLayer.FREESHIP && voucher.getRewardType() == Voucher.RewardType.FREESHIP) {
                return "Freeship reward cannot be used as an item discount";
            }
        } catch (IllegalArgumentException invalidLayer) {
            return invalidLayer.getMessage() == null ? "Voucher layer is invalid" : invalidLayer.getMessage();
        }
        return null;
    }
}
