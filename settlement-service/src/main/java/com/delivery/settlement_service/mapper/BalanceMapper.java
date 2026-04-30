package com.delivery.settlement_service.mapper;

import com.delivery.settlement_service.dto.response.BalanceResponse;
import com.delivery.settlement_service.entity.Balance;
import org.springframework.stereotype.Component;

@Component
public class BalanceMapper {

    public BalanceResponse toResponse(Balance balance) {
        if (balance == null) {
            return null;
        }

        return BalanceResponse.builder()
                .id(balance.getId())
                .entityId(balance.getEntityId())
                .entityType(balance.getEntityType().name())
                .availableBalance(balance.getAvailableBalance())
                .pendingBalance(balance.getPendingBalance())
                .holdingBalance(balance.getHoldingBalance())
                .depositBalance(balance.getDepositBalance())
                .totalDeposited(balance.getTotalDeposited())
                .totalCodCollected(balance.getTotalCodCollected())
                .createdAt(balance.getCreatedAt())
                .updatedAt(balance.getUpdatedAt())
                .build();
    }
}
