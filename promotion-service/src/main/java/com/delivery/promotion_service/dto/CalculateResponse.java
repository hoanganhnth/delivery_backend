package com.delivery.promotion_service.dto;

import com.delivery.promotion_service.entity.Voucher;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculateResponse {
    private List<VoucherInfo> availableVouchers;
    private List<UnavailableVoucherInfo> unavailableVouchers;
    private BigDecimal finalSubTotal;
    private BigDecimal finalShippingFee;
    private BigDecimal totalDiscount;
    private BigDecimal totalAmount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VoucherInfo {
        private Long id;
        private String code;
        private String name;
        private Voucher.RewardType rewardType;
        private BigDecimal discountValue;
        private Long voucherGroupId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnavailableVoucherInfo {
        private Long id;
        private String code;
        private String name;
        private String reason;
    }
}
