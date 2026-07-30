package com.delivery.tracking_service.service;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipperAvailabilityService {

    private final ShipperLocationRepository repository;
    private final ShipperLocationEventPublisher eventPublisher;

    public ShipperLocationResponse markOffline(Long shipperId) {
        ShipperLocationResponse location = repository.getCachedShipperLocation(shipperId);
        boolean hadCachedLocation = location != null;
        String serverTimestamp = LocalDateTime.now().toString();
        if (location == null) {
            repository.removeShipperLocationCache(shipperId);
            location = new ShipperLocationResponse();
            location.setShipperId(shipperId);
        }

        location.setIsOnline(false);
        location.setLastPing(serverTimestamp);
        location.setUpdatedAt(serverTimestamp);
        if (hadCachedLocation) {
            repository.cacheShipperLocation(shipperId, location);
        }

        eventPublisher.publishLocationUpdate(location, "OFFLINE_TOMBSTONE");
        log.info("🔴 Marked shipper {} offline and published Match tombstone", shipperId);
        return location;
    }
}
