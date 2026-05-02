package com.delivery.promotion_service.dto;

import com.delivery.promotion_service.entity.Voucher;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateVoucherRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String description;

    @NotNull
    private Voucher.CreatorType creatorType;
    private Long creatorId;

    @NotNull
    private Voucher.RewardType rewardType;

    @NotNull
    @Min(0)
    private BigDecimal discountValue;

    private BigDecimal maxDiscountValue;

    @NotNull
    private Voucher.ScopeType scopeType;
    private Long scopeRefId;

    @NotNull
    @Min(1)
    private Integer totalQuantity;

    @NotNull
    @Min(1)
    private Integer usageLimitPerUser;

    @NotNull
    private LocalDateTime startTime;

    @NotNull
    @Future
    private LocalDateTime endTime;

    @NotNull
    @Min(0)
    private BigDecimal minOrderValue;

    private Long voucherGroupId;
    private String customerSegment;
}
