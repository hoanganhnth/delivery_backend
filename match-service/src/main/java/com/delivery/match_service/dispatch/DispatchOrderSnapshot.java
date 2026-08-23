package com.delivery.match_service.dispatch;

import java.time.LocalDateTime;
import java.util.UUID;

public record DispatchOrderSnapshot(
        UUID poolItemId,
        Long orderId,
        Long deliveryId,
        double pickupLat,
        double pickupLng,
        double deliveryLat,
        double deliveryLng,
        LocalDateTime matchingDeadlineAt) {
}
