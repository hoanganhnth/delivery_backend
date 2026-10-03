package com.delivery.tracking_service.service;

import com.delivery.tracking.application.api.ShipperAvailabilityUseCase;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Wire DTO adapter used by publisher lease/grace callbacks. */
@Service
@RequiredArgsConstructor
public class ShipperAvailabilityService {
    private final ShipperAvailabilityUseCase availability;

    public ShipperLocationResponse markOffline(Long shipperId) {
        return RedisShipperAvailabilityAdapter.toResponse(availability.markOffline(shipperId));
    }

    public ShipperLocationResponse markOfflineAndBroadcast(Long shipperId) {
        return RedisShipperAvailabilityAdapter.toResponse(availability.markOfflineAndBroadcast(shipperId));
    }
}
