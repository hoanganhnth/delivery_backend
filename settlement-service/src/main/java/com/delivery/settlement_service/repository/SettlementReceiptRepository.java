package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.SettlementReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettlementReceiptRepository extends JpaRepository<SettlementReceipt, UUID> {
    Optional<SettlementReceipt> findByOrderId(Long orderId);
}
