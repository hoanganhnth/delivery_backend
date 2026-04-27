package com.delivery.tracking_service.service;

import com.delivery.tracking_service.dto.request.UpdateLocationRequest;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperLocationRepository;
import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipperLocationService {

    // ✅ Use Redis GEO service instead of basic Redis service
    private final ShipperLocationRepository redisGeoRepository;
    
    // ✅ WebSocket handler để broadcast real-time updates
    private final ShipperLocationWebSocketHandler webSocketHandler;

    /**
     * ✅ Update shipper location with Redis GEO support theo Backend Instructions
     */
    public ShipperLocationResponse updateLocation(Long shipperId, UpdateLocationRequest request) {
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
            webSocketHandler.broadcastAreaLocationUpdate(response);

            log.info("✅ Updated location for shipper: {} at ({}, {}) - Online: {} [Redis GEO + WebSocket]",
                    shipperId, request.getLatitude(), request.getLongitude(), request.getIsOnline());

            return response;

        } catch (Exception e) {
            log.error("💥 Error updating shipper location: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể cập nhật vị trí shipper");
        }
    }

    /**
     * Get shipper location from Redis cache
     */
    public Optional<ShipperLocationResponse> getShipperLocation(Long shipperId) {
        try {
            ShipperLocationResponse cached = redisGeoRepository.getCachedShipperLocation(shipperId);
            if (cached != null) {
                log.debug("📍 Retrieved location for shipper: {} from Redis cache", shipperId);
                return Optional.of(cached);
            }

            log.debug("⚠️ No location found for shipper: {}", shipperId);
            return Optional.empty();

        } catch (Exception e) {
            log.error("💥 Error getting shipper location: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * ✅ Find nearby shippers using Redis GEO operations theo Backend Instructions
     */
    public List<ShipperLocationResponse> findNearbyShippers(Double centerLat,
            Double centerLng,
            Double radiusKm,
            Integer limit) {
        try {
            // ✅ Use Redis GEORADIUS command for spatial query
            List<ShipperLocationResponse> nearbyShippers = redisGeoRepository.findShippersWithinRadius(
                    centerLat, centerLng, radiusKm, limit);

            log.info("🔍 Found {} shippers within {}km from ({}, {}) using Redis GEO",
                    nearbyShippers.size(), radiusKm, centerLat, centerLng);

            return nearbyShippers;

        } catch (Exception e) {
            log.error("💥 Error finding nearby shippers with Redis GEO: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * ✅ Get distance between two shippers using Redis GEODIST
     */
    public Double getDistanceBetweenShippers(Long shipperId1, Long shipperId2) {
        try {
            Double distance = redisGeoRepository.getDistanceBetweenShippers(shipperId1, shipperId2);

            if (distance != null) {
                log.debug("📏 Distance between shipper {} and {}: {}km", shipperId1, shipperId2, distance);
            } else {
                log.warn("⚠️ Could not calculate distance between shipper {} and {}", shipperId1, shipperId2);
            }

            return distance;

        } catch (Exception e) {
            log.error("💥 Error calculating distance between shippers: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Mark shipper offline - updated with Redis GEO support
     */
    public void markShipperOffline(Long shipperId) {
        try {
            Optional<ShipperLocationResponse> currentLocation = getShipperLocation(shipperId);

            if (currentLocation.isPresent()) {
                // Case 1: Có location trong cache → Update isOnline = false
                ShipperLocationResponse location = currentLocation.get();
                location.setIsOnline(false);
                location.setUpdatedAt(LocalDateTime.now().toString()); // Backend tự generate timestamp

                // ✅ Save updated location using Redis GEO service
                redisGeoRepository.cacheShipperLocation(shipperId, location);
                log.info("🔴 Marked shipper {} as offline (updated existing location) [Redis GEO]", shipperId);

            } else {
                // Case 2: Không có location trong cache → Remove completely
                redisGeoRepository.removeShipperLocationCache(shipperId);
                log.info("🔴 Removed offline shipper {} from Redis GEO cache", shipperId);
            }

        } catch (Exception e) {
            log.error("💥 Error marking shipper {} offline: {}", shipperId, e.getMessage(), e);
            // Fallback: Remove from cache completely
            redisGeoRepository.removeShipperLocationCache(shipperId);
        }
    }

    /**
     * Get all online shippers - optimized with Redis SET
     */
    public List<ShipperLocationResponse> getOnlineShippers() {
        try {
            // ✅ Use optimized Redis SET query instead of scanning all keys
            List<ShipperLocationResponse> onlineShippers = redisGeoRepository.getAllOnlineShippers();

            log.info("📋 Retrieved {} online shippers from Redis GEO cache", onlineShippers.size());
            return onlineShippers;

        } catch (Exception e) {
            log.error("💥 Error getting online shippers: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * ✅ Đánh dấu shipper đang bận (đang có đơn hàng)
     * Gọi bởi delivery-service khi shipper nhận đơn
     */
    public void markShipperBusy(Long shipperId) {
        redisGeoRepository.markShipperBusy(shipperId);
        log.info("🔴 Shipper {} marked as BUSY - excluded from matching", shipperId);
    }

    /**
     * ✅ Đánh dấu shipper rảnh (hoàn thành hoặc huỷ đơn)
     * Gọi bởi delivery-service khi shipper hoàn thành/huỷ đơn
     */
    public void markShipperAvailable(Long shipperId) {
        redisGeoRepository.markShipperAvailable(shipperId);
        log.info("🟢 Shipper {} marked as AVAILABLE - eligible for matching", shipperId);
    }
}
