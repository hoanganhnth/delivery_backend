package com.delivery.simulator.service;

import java.util.List;
import java.util.UUID;

/** Private Delivery lookup used only when an old run journal lacks delivery IDs. */
public interface SimulationDeliveryRecoveryClient {
    List<DeliveryStatus> findByRunId(UUID runId);

    record DeliveryStatus(Long deliveryId, Long orderId, String status) { }
}
