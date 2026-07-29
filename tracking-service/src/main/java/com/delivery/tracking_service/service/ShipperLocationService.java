package com.delivery.tracking_service.service;

import com.delivery.tracking_service.dto.request.UpdateLocationRequest;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperLocationRepository;
import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipperLocationService {

    // ✅ Use Redis GEO service instead of basic Redis service
    private final ShipperLocationRepository redisGeoRepository;
    
    // ✅ WebSocket handler để broadcast real-time updates
    private final ShipperLocationWebSocketHandler webSocketHandler;

    // ✅ Kafka publisher để replicate vị trí sang match-service
    private final ShipperLocationEventPublisher locationEventPublisher;

    private final ShipperAvailabilityService availabilityService;

    /**
     * ✅ Update shipper location with Redis GEO support theo Backend Instructions
     */
    public ShipperLocationResponse updateLocation(Long shipperId, UpdateLocationRequest request) {
        validateUpdateRequest(request);
        try {
            // Create response object with location data
            ShipperLocationResponse response = new ShipperLocationResponse();
            response.setShipperId(shipperId);
            response.setLatitude(request.getLatitude());
            response.setLongitude(request.getLongitude());
            response.setAccuracy(request.getAccuracy());
            response.setSpeed(request.getSpeed());
            response.setHeading(request.getHeading());
            response.setIsOnline(request.getIsOnline());

            // ✅ Server-side timestamps theo Backend Instructions
            LocalDateTime now = LocalDateTime.now();
            response.setLastPing(now.toString());
            response.setUpdatedAt(now.toString());

            // ✅ Cache using Redis GEO service
            redisGeoRepository.cacheShipperLocation(shipperId, response);

            // ✅ Broadcast vị trí mới qua WebSocket cho các client đang theo dõi
            webSocketHandler.broadcastShipperLocation(response);

            // ✅ Publish vị trí qua Kafka để match-service replicate
            locationEventPublisher.publishLocationUpdate(
                    shipperId, request.getLatitude(), request.getLongitude(), request.getIsOnline());

            log.info("✅ Updated location for shipper: {} at ({}, {}) - Online: {} [Redis GEO + WebSocket + Kafka]",
                    shipperId, request.getLatitude(), request.getLongitude(), request.getIsOnline());

            return response;

        } catch (Exception e) {
            log.error("💥 Error updating shipper location: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể cập nhật vị trí shipper");
        }
    }

    private void validateUpdateRequest(UpdateLocationRequest request) {
        requireFiniteInRange(request.getLatitude(), "latitude", -90.0, 90.0);
        requireFiniteInRange(request.getLongitude(), "longitude", -180.0, 180.0);
        requireFiniteIfPresent(request.getAccuracy(), "accuracy");
        requireFiniteIfPresent(request.getSpeed(), "speed");
        requireFiniteIfPresent(request.getHeading(), "heading");
        if (request.getIsOnline() == null) {
            throw new IllegalArgumentException("isOnline must be boolean");
        }
    }

    private void requireFiniteInRange(Double value, String field, double min, double max) {
        if (value == null || !Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private void requireFiniteIfPresent(Double value, String field) {
        if (value != null && !Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    /**
     * Mark shipper offline - updated with Redis GEO support
     */
    public void markShipperOffline(Long shipperId) {
        ShipperLocationResponse location = availabilityService.markOffline(shipperId);
        webSocketHandler.broadcastShipperLocation(location);
    }

}
