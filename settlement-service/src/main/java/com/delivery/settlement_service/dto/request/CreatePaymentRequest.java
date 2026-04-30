package com.delivery.settlement_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request tạo giao dịch thanh toán qua cổng bên thứ ba
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

    @NotNull(message = "Entity ID is required")
    private Long entityId;

    /**
     * Loại entity: SHIPPER (mặc định), CUSTOMER (future)
     */
    private String entityType = "SHIPPER";

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10000", message = "Minimum payment is 10,000 VND")
    private BigDecimal amount;

    /**
     * Nhà cung cấp thanh toán: VNPAY, MOMO, FAKE...
     * Nếu không truyền → dùng giá trị mặc định từ config
     */
    private String provider;

    /**
     * Mục đích: DEPOSIT_TOPUP (default), ORDER_PAYMENT...
     */
    private String purpose = "DEPOSIT_TOPUP";

    /**
     * URL redirect sau khi thanh toán xong (dùng cho VNPay, MoMo...)
     */
    private String returnUrl;

    /**
     * IP khách hàng (VNPay bắt buộc)
     */
    private String ipAddress;
}
