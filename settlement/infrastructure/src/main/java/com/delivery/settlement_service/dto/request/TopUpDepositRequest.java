package com.delivery.settlement_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body cho shipper nạp tiền vào Ví Ký quỹ
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopUpDepositRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10000", message = "Minimum top-up is 10,000 VND")
    private BigDecimal amount;

    /**
     * Phương thức nạp: BANK_TRANSFER, MOMO, ZALOPAY, CASH
     */
    private String paymentMethod;
}
