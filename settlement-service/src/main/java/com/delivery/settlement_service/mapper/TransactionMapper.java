package com.delivery.settlement_service.mapper;

import com.delivery.settlement_service.dto.response.TransactionResponse;
import com.delivery.settlement_service.entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        return TransactionResponse.builder()
                .id(transaction.getId())
                .entityId(transaction.getEntityId())
                .entityType(transaction.getEntityType().name())
                .orderId(transaction.getOrderId())
                .direction(transaction.getDirection().name())
                .reason(transaction.getReason().name())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .status(transaction.getStatus().name())
                .walletType(transaction.getWalletType().name())
                .processedAt(transaction.getProcessedAt())
                .processedBy(transaction.getProcessedBy())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
