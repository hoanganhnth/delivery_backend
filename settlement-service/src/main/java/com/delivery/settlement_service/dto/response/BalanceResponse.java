package com.delivery.settlement_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceResponse {
    private Long id;
    private Long entityId;
    private String entityType;
    private BigDecimal availableBalance;   // Ví Thu nhập
    private BigDecimal pendingBalance;
    private BigDecimal holdingBalance;
    private BigDecimal depositBalance;     // Ví Ký quỹ (shipper only)
    private BigDecimal totalDeposited;     // Tổng đã nạp
    private BigDecimal totalCodCollected;  // Tổng COD đã thu
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
