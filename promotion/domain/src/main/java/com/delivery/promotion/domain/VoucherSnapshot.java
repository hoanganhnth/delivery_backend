package com.delivery.promotion.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VoucherSnapshot(
        Long getId,
        String getCode,
        CreatorType getCreatorType,
        RewardType getRewardType,
        ScopeType getScopeType,
        Long getScopeRefId,
        BigDecimal getDiscountValue,
        BigDecimal getMaxDiscountValue,
        BigDecimal getMinOrderValue,
        Integer getTotalQuantity,
        Integer getUsedQuantity,
        Boolean getActive,
        LocalDateTime getStartTime,
        LocalDateTime getEndTime,
        LocalDateTime getDeletedAt,
        String getLayerCode,
        String getApprovalStatus) implements Voucher {
}
