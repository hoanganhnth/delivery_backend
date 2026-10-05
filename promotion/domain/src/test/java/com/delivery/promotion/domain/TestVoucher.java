package com.delivery.promotion.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

final class TestVoucher implements Voucher {
    private Long id;
    private String code;
    private CreatorType creatorType;
    private RewardType rewardType;
    private ScopeType scopeType;
    private Long scopeRefId;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountValue;
    private BigDecimal minOrderValue;
    private Integer totalQuantity;
    private Integer usedQuantity = 0;
    private Boolean active = true;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime deletedAt;
    private String layerCode;
    private String approvalStatus;
    public Long getId() { return id; }
    void setId(Long value) { id = value; }
    public String getCode() { return code; }
    void setCode(String value) { code = value; }
    public CreatorType getCreatorType() { return creatorType; }
    void setCreatorType(CreatorType value) { creatorType = value; }
    public RewardType getRewardType() { return rewardType; }
    void setRewardType(RewardType value) { rewardType = value; }
    public ScopeType getScopeType() { return scopeType; }
    void setScopeType(ScopeType value) { scopeType = value; }
    public Long getScopeRefId() { return scopeRefId; }
    void setScopeRefId(Long value) { scopeRefId = value; }
    public BigDecimal getDiscountValue() { return discountValue; }
    void setDiscountValue(BigDecimal value) { discountValue = value; }
    public BigDecimal getMaxDiscountValue() { return maxDiscountValue; }
    void setMaxDiscountValue(BigDecimal value) { maxDiscountValue = value; }
    public BigDecimal getMinOrderValue() { return minOrderValue; }
    void setMinOrderValue(BigDecimal value) { minOrderValue = value; }
    public Integer getTotalQuantity() { return totalQuantity; }
    void setTotalQuantity(Integer value) { totalQuantity = value; }
    public Integer getUsedQuantity() { return usedQuantity; }
    void setUsedQuantity(Integer value) { usedQuantity = value; }
    public Boolean getActive() { return active; }
    void setActive(Boolean value) { active = value; }
    public LocalDateTime getStartTime() { return startTime; }
    void setStartTime(LocalDateTime value) { startTime = value; }
    public LocalDateTime getEndTime() { return endTime; }
    void setEndTime(LocalDateTime value) { endTime = value; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    void setDeletedAt(LocalDateTime value) { deletedAt = value; }
    public String getLayerCode() { return layerCode; }
    void setLayerCode(String value) { layerCode = value; }
    public String getApprovalStatus() { return approvalStatus; }
    void setApprovalStatus(String value) { approvalStatus = value; }
    static Builder builder() { return new Builder(); }
    static final class Builder {
        private final TestVoucher voucher = new TestVoucher();
        Builder id(Long value) { voucher.id = value; return this; }
        Builder code(String value) { voucher.code = value; return this; }
        Builder creatorType(CreatorType value) { voucher.creatorType = value; return this; }
        Builder rewardType(RewardType value) { voucher.rewardType = value; return this; }
        Builder scopeType(ScopeType value) { voucher.scopeType = value; return this; }
        Builder scopeRefId(Long value) { voucher.scopeRefId = value; return this; }
        Builder discountValue(BigDecimal value) { voucher.discountValue = value; return this; }
        Builder maxDiscountValue(BigDecimal value) { voucher.maxDiscountValue = value; return this; }
        Builder minOrderValue(BigDecimal value) { voucher.minOrderValue = value; return this; }
        Builder totalQuantity(Integer value) { voucher.totalQuantity = value; return this; }
        Builder usedQuantity(Integer value) { voucher.usedQuantity = value; return this; }
        Builder active(Boolean value) { voucher.active = value; return this; }
        Builder startTime(LocalDateTime value) { voucher.startTime = value; return this; }
        Builder endTime(LocalDateTime value) { voucher.endTime = value; return this; }
        Builder deletedAt(LocalDateTime value) { voucher.deletedAt = value; return this; }
        Builder layerCode(String value) { voucher.layerCode = value; return this; }
        Builder approvalStatus(String value) { voucher.approvalStatus = value; return this; }
        Builder name(String value) { return this; }
        Builder fundingSource(String value) { return this; }
        Builder usageLimitPerUser(Integer value) { return this; }
        TestVoucher build() { return voucher; }
    }
    void setFundingSource(String value) {}
}
