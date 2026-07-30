package com.delivery.saga_orchestrator_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.delivery.saga_orchestrator_service.entity.SagaInboundReceipt;

public interface SagaInboundReceiptRepository extends JpaRepository<SagaInboundReceipt, UUID> { }
