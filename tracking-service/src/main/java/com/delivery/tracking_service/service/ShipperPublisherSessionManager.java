package com.delivery.tracking_service.service;

import com.delivery.tracking.application.api.PublisherSessionUseCase;
import com.delivery.tracking.domain.PublisherLease;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** WebSocket DTO/callback adapter to the actual publisher session application core. */
@Service
@RequiredArgsConstructor
public class ShipperPublisherSessionManager {
    private final PublisherSessionUseCase publishers;

    public PublisherLease acquire(Long shipperId, String sessionId) {
        return publishers.acquire(shipperId, sessionId);
    }
    public boolean refreshIfCurrent(PublisherLease lease) {
        return publishers.refreshIfCurrent(lease);
    }
    public void disconnected(PublisherLease lease, Consumer<ShipperLocationResponse> afterOffline) {
        publishers.disconnected(lease, offline -> afterOffline.accept(RedisShipperAvailabilityAdapter.toResponse(offline)));
    }
}
