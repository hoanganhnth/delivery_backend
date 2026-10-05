package com.delivery.promotion_service.service;

import com.delivery.promotion.domain.VoucherSnapshot;
import com.delivery.promotion_service.entity.Voucher;

final class VoucherDomainMapper {
    private VoucherDomainMapper() {}

    static IllegalArgumentException creationFailure(IllegalArgumentException failure) {
        if (failure.getCause() == null) return failure;
        // Enum.valueOf includes its declaring class in the cause message.
        // Preserve the legacy host enum name as well as the public validation message.
        var cause = new IllegalArgumentException(failure.getCause().getMessage().replace(
                "com.delivery.promotion.domain.VoucherLayer", VoucherLayer.class.getName()));
        return new IllegalArgumentException(failure.getMessage(), cause);
    }

    static com.delivery.promotion.domain.CampaignPolicy.Campaign campaign(
            com.delivery.promotion_service.dto.CreateVoucherRequest request) {
        if (request == null) return null;
        return new com.delivery.promotion.domain.CampaignPolicy.Campaign(
                request.getCode(),
                request.getName(),
                request.getCreatorType() == null ? null : com.delivery.promotion.domain.Voucher.CreatorType.valueOf(request.getCreatorType().name()),
                request.getRewardType() == null ? null : com.delivery.promotion.domain.Voucher.RewardType.valueOf(request.getRewardType().name()),
                request.getScopeType() == null ? null : com.delivery.promotion.domain.Voucher.ScopeType.valueOf(request.getScopeType().name()),
                request.getDiscountValue(),
                request.getMaxDiscountValue(),
                request.getTotalQuantity(),
                request.getUsageLimitPerUser(),
                request.getStartTime(),
                request.getEndTime(),
                request.getMinOrderValue(),
                request.getScopeRefId(),
                request.getRestaurantId(),
                request.getOwnerPrincipalId(),
                request.getLayerCode());
    }

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
