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
public class TransactionResponse {
    private Long id;
    private Long entityId;
    private String entityType;
    private Long orderId;
    private String direction;
    private String reason;
    private BigDecimal amount;
    private String description;
    private String status;
    private LocalDateTime processedAt;
    private Long processedBy;
    private LocalDateTime createdAt;
}
