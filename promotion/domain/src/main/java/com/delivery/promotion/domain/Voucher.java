package com.delivery.promotion.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface Voucher {
    Long getId();
    String getCode();
    CreatorType getCreatorType();
    RewardType getRewardType();
    ScopeType getScopeType();
    Long getScopeRefId();
    BigDecimal getDiscountValue();
    BigDecimal getMaxDiscountValue();
    BigDecimal getMinOrderValue();
    Integer getTotalQuantity();
    Integer getUsedQuantity();
    Boolean getActive();
    LocalDateTime getStartTime();
    LocalDateTime getEndTime();
    LocalDateTime getDeletedAt();
    String getLayerCode();
    String getApprovalStatus();

    enum CreatorType { PLATFORM, MERCHANT, SHOP }
    enum RewardType { FIXED, PERCENTAGE, FREESHIP }
    enum ScopeType { ALL, SHOP, CATEGORY }
}
