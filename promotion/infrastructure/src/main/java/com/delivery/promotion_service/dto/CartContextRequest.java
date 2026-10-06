package com.delivery.promotion_service.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartContextRequest {
    @NotNull
    @Positive
    private Long shopId;
    private Long userId;
    @Positive
    private Long userPrincipalId;
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal subTotal;
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal shippingFee;
    @Positive
    private Long selectedVoucherId;
    /** New stacking contract; selectedVoucherId remains for legacy callers. */
    private List<@Positive Long> selectedVoucherIds;
    private VoucherSelectionMode selectionMode;
    // Assuming a simplified cart representation
    // private List<CartItem> items; 
}
