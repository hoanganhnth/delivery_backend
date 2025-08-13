
package com.delivery.tracking_service.repository;
import org.springframework.stereotype.Repository;

import com.delivery.tracking_service.common.constants.RedisConstants;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisGeoRepository implements ShipperLocationRepository {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String GEO_KEY = "shippers:geo:locations";
    private static final String ONLINE_SHIPPERS_SET = "shippers:online:set";

    // --- BEGIN: Method implement từ RedisGeoService ---
    public void cacheShipperLocation(Long shipperId, ShipperLocationResponse location) {
        try {
            String detailKey = RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + shipperId;
            redisTemplate.opsForValue().set(detailKey, location, RedisConstants.SHIPPER_LOCATION_TTL, TimeUnit.SECONDS);
            if (location.getLatitude() != null && location.getLongitude() != null) {
                GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
                Point point = new Point(location.getLongitude(), location.getLatitude());
                geoOps.add(GEO_KEY, point, shipperId.toString());
                redisTemplate.expire(GEO_KEY, RedisConstants.SHIPPER_LOCATION_TTL, TimeUnit.SECONDS);
                log.debug("✅ Cached GEO location for shipper: {} at ({}, {})", shipperId, location.getLatitude(), location.getLongitude());
            }
            if (Boolean.TRUE.equals(location.getIsOnline())) {
                redisTemplate.opsForSet().add(ONLINE_SHIPPERS_SET, shipperId.toString());
                redisTemplate.expire(ONLINE_SHIPPERS_SET, RedisConstants.SHIPPER_LOCATION_TTL, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForSet().remove(ONLINE_SHIPPERS_SET, shipperId.toString());
            }
        } catch (Exception e) {
            log.error("💥 Error caching shipper location with GEO: {}", e.getMessage(), e);
        }
    }

    public ShipperLocationResponse getCachedShipperLocation(Long shipperId) {
        try {
            String key = RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + shipperId;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof ShipperLocationResponse) {
                log.debug("Retrieved cached location for shipper: {}", shipperId);
                return (ShipperLocationResponse) cached;
            }
            log.debug("No cached location found for shipper: {}", shipperId);
            return null;
        } catch (Exception e) {
            log.error("💥 Error getting cached location: {}", e.getMessage(), e);
            return null;
        }
    }

    public List<ShipperLocationResponse> findShippersWithinRadius(Double centerLat, Double centerLng, Double radiusKm, Integer limit) {
        List<ShipperLocationResponse> nearbyShippers = new ArrayList<>();
        try {
            GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
            Point center = new Point(centerLng, centerLat);
            Distance radius = new Distance(radiusKm, Metrics.KILOMETERS);
            Circle circle = new Circle(center, radius);
            RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                    .newGeoRadiusArgs()
                    .includeDistance()
                    .includeCoordinates()
                    .sortAscending()
                    .limit(limit != null ? limit : 10);
            org.springframework.data.geo.GeoResults<RedisGeoCommands.GeoLocation<Object>> geoResults = geoOps.radius(GEO_KEY, circle, args);
            if (geoResults != null && geoResults.getContent() != null) {
                for (var geoResult : geoResults.getContent()) {
                    try {
                        String shipperIdStr = geoResult.getContent().getName().toString();
                        Long shipperId = Long.parseLong(shipperIdStr);
                        boolean isOnline = redisTemplate.opsForSet().isMember(ONLINE_SHIPPERS_SET, shipperIdStr);
                        if (isOnline) {
                            ShipperLocationResponse location = getCachedShipperLocation(shipperId);
                            if (location != null) {
                                location.setDistance(geoResult.getDistance().getValue());
                                nearbyShippers.add(location);
                            }
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Invalid shipper ID in GEO results: {}", geoResult.getContent().getName());
                    }
                }
            }
            log.info("🔍 Found {} online shippers within {}km from ({}, {}) using Redis GEO", nearbyShippers.size(), radiusKm, centerLat, centerLng);
        } catch (Exception e) {
            log.error("💥 Error finding shippers with Redis GEO: {}", e.getMessage(), e);
        }
        return nearbyShippers;
    }

    public Double getDistanceBetweenShippers(Long shipperId1, Long shipperId2) {
        try {
            GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
            Distance distance = geoOps.distance(GEO_KEY, shipperId1.toString(), shipperId2.toString(), Metrics.KILOMETERS);
            return distance != null ? distance.getValue() : null;
        } catch (Exception e) {
            log.error("💥 Error calculating distance between shippers: {}", e.getMessage(), e);
            return null;
        }
    }

    public Point getShipperGeoPosition(Long shipperId) {
        try {
            GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
            List<Point> positions = geoOps.position(GEO_KEY, shipperId.toString());
            return (positions != null && !positions.isEmpty()) ? positions.get(0) : null;
        } catch (Exception e) {
            log.error("💥 Error getting shipper GEO position: {}", e.getMessage(), e);
            return null;
        }
    }

    public List<ShipperLocationResponse> getAllOnlineShippers() {
        List<ShipperLocationResponse> onlineShippers = new ArrayList<>();
        try {
            Set<Object> onlineShipperIds = redisTemplate.opsForSet().members(ONLINE_SHIPPERS_SET);
            if (onlineShipperIds != null && !onlineShipperIds.isEmpty()) {
                for (Object shipperIdObj : onlineShipperIds) {
                    try {
                        Long shipperId = Long.parseLong(shipperIdObj.toString());
                        ShipperLocationResponse location = getCachedShipperLocation(shipperId);
                        if (location != null && Boolean.TRUE.equals(location.getIsOnline())) {
                            onlineShippers.add(location);
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Invalid shipper ID in online set: {}", shipperIdObj);
                    }
                }
            }
            log.debug("📋 Retrieved {} online shippers from Redis SET", onlineShippers.size());
        } catch (Exception e) {
            log.error("💥 Error retrieving online shippers: {}", e.getMessage(), e);
        }
        return onlineShippers;
    }

    public void removeShipperLocationCache(Long shipperId) {
        try {
            String detailKey = RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + shipperId;
            redisTemplate.delete(detailKey);
            GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
            geoOps.remove(GEO_KEY, shipperId.toString());
            redisTemplate.opsForSet().remove(ONLINE_SHIPPERS_SET, shipperId.toString());
            log.debug("🗑️ Removed shipper {} from all Redis caches", shipperId);
        } catch (Exception e) {
            log.error("💥 Error removing shipper from cache: {}", e.getMessage(), e);
        }
    }

    public boolean isRedisAvailable() {
        try {
            redisTemplate.opsForValue().set("health:check", "OK", 5, TimeUnit.SECONDS);
            return "OK".equals(redisTemplate.opsForValue().get("health:check"));
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return false;
        }
    }

    public int getActiveConnections() {
        try {
            return redisTemplate.getConnectionFactory() != null ? 1 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getTotalConnections() {
        try {
            return redisTemplate.getConnectionFactory() != null ? 1 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getCachedShippersCount() {
        try {
            Set<Object> onlineShippers = redisTemplate.opsForSet().members(ONLINE_SHIPPERS_SET);
            return onlineShippers != null ? onlineShippers.size() : 0;
        } catch (Exception e) {
            log.error("Error getting cached shippers count: {}", e.getMessage());
            return 0;
        }
    }
    // --- END ---
}
