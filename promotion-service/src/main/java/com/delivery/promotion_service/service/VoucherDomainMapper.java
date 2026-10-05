package com.delivery.promotion_service.service;

import com.delivery.promotion.domain.VoucherSnapshot;
import com.delivery.promotion_service.entity.Voucher;

final class VoucherDomainMapper {
    private VoucherDomainMapper() {}

    static VoucherSnapshot snapshot(Voucher voucher) {
        if (voucher == null) return null;
        return new VoucherSnapshot(
                voucher.getId(),
                voucher.getCode(),
                voucher.getCreatorType() == null ? null : com.delivery.promotion.domain.Voucher.CreatorType.valueOf(voucher.getCreatorType().name()),
                voucher.getRewardType() == null ? null : com.delivery.promotion.domain.Voucher.RewardType.valueOf(voucher.getRewardType().name()),
                voucher.getScopeType() == null ? null : com.delivery.promotion.domain.Voucher.ScopeType.valueOf(voucher.getScopeType().name()),
                voucher.getScopeRefId(),
                voucher.getDiscountValue(),
                voucher.getMaxDiscountValue(),
                voucher.getMinOrderValue(),
                voucher.getTotalQuantity(),
                voucher.getUsedQuantity(),
                voucher.getActive(),
                voucher.getStartTime(),
                voucher.getEndTime(),
                voucher.getDeletedAt(),
                voucher.getLayerCode(),
                voucher.getApprovalStatus());
    }
}
