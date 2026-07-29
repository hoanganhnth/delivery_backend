package com.delivery.promotion_service.dto;

import com.delivery.promotion_service.entity.Voucher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Stable HTTP DTO; persistence entities must not be serialized by controllers. */
public record VoucherResponse(
        Long id,
        String code,
        String name,
        String description,
        Voucher.CreatorType creatorType,
        Long creatorId,
        Voucher.RewardType rewardType,
        BigDecimal discountValue,
        BigDecimal maxDiscountValue,
        Voucher.ScopeType scopeType,
        Long scopeRefId,
        Integer totalQuantity,
        Integer usedQuantity,
        Integer usageLimitPerUser,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal minOrderValue,
        Long voucherGroupId,
        String customerSegment,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static VoucherResponse from(Voucher voucher) {
        if (voucher == null) {
            throw new IllegalArgumentException("Voucher is required");
        }
        return new VoucherResponse(
                voucher.getId(),
                voucher.getCode(),
                voucher.getName(),
                voucher.getDescription(),
                voucher.getCreatorType(),
                voucher.getCreatorId(),
                voucher.getRewardType(),
                voucher.getDiscountValue(),
                voucher.getMaxDiscountValue(),
                voucher.getScopeType(),
                voucher.getScopeRefId(),
                voucher.getTotalQuantity(),
                voucher.getUsedQuantity(),
                voucher.getUsageLimitPerUser(),
                voucher.getStartTime(),
                voucher.getEndTime(),
                voucher.getMinOrderValue(),
                voucher.getVoucherGroupId(),
                voucher.getCustomerSegment(),
                voucher.getActive(),
                voucher.getCreatedAt(),
                voucher.getUpdatedAt());
    }
}
