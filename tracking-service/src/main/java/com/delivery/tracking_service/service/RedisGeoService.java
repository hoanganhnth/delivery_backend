package com.delivery.tracking_service.service;

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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisGeoService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    // ✅ Redis GEO keys for spatial operations theo Backend Instructions
    private static final String GEO_KEY = "shippers:geo:locations";
    private static final String ONLINE_SHIPPERS_SET = "shippers:online:set";

    /**
     * ✅ Cache shipper location using Redis GEO + detailed data
     * Theo Backend Instructions: Dual storage approach
     */
    public void cacheShipperLocation(Long shipperId, ShipperLocationResponse location) {
        try {
            // 1. Cache detailed location data for full information
            String detailKey = RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + shipperId;
            redisTemplate.opsForValue().set(detailKey, location, RedisConstants.SHIPPER_LOCATION_TTL, TimeUnit.SECONDS);
            
            // 2. ✅ Cache GEO location for spatial queries using Redis GEO
            if (location.getLatitude() != null && location.getLongitude() != null) {
                GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
                Point point = new Point(location.getLongitude(), location.getLatitude()); // Note: lng, lat order!
                
                // Add to GEO set với member name = shipperId
                geoOps.add(GEO_KEY, point, shipperId.toString());
                redisTemplate.expire(GEO_KEY, RedisConstants.SHIPPER_LOCATION_TTL, TimeUnit.SECONDS);
                
                log.debug("✅ Cached GEO location for shipper: {} at ({}, {})", 
                    shipperId, location.getLatitude(), location.getLongitude());
            }
            
            // 3. ✅ Track online status separately for fast filtering
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

    /**
     * Get cached location details (existing method)
     */
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

    /**
     * ✅ Find shippers within radius using Redis GEORADIUS command
     * Theo Backend Instructions: Native Redis GEO operations
     */
    public List<ShipperLocationResponse> findShippersWithinRadius(Double centerLat, 
                                                                Double centerLng, 
                                                                Double radiusKm, 
                                                                Integer limit) {
        List<ShipperLocationResponse> nearbyShippers = new ArrayList<>();
        
        try {
            GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
            
            // ✅ Use Redis GEORADIUS for spatial query
            Point center = new Point(centerLng, centerLat); // Note: lng, lat order!
            Distance radius = new Distance(radiusKm, Metrics.KILOMETERS);
            Circle circle = new Circle(center, radius);
            
            // GEORADIUS with distance and coordinates
            RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                    .newGeoRadiusArgs()
                    .includeDistance()
                    .includeCoordinates()
                    .sortAscending() // Sort by distance (nearest first)
                    .limit(limit != null ? limit : 10);
            
            // Execute GEORADIUS command
            org.springframework.data.geo.GeoResults<RedisGeoCommands.GeoLocation<Object>> geoResults = 
                    geoOps.radius(GEO_KEY, circle, args);
            
            // Process results
            if (geoResults != null && geoResults.getContent() != null) {
                for (var geoResult : geoResults.getContent()) {
                    try {
                        String shipperIdStr = geoResult.getContent().getName().toString();
                        Long shipperId = Long.parseLong(shipperIdStr);
                        
                        // ✅ Check if shipper is online
                        boolean isOnline = redisTemplate.opsForSet().isMember(ONLINE_SHIPPERS_SET, shipperIdStr);
                        
                        if (isOnline) {
                            // Get detailed location info
                            ShipperLocationResponse location = getCachedShipperLocation(shipperId);
                            
                            if (location != null) {
                                // ✅ Add distance information from Redis GEO result
                                location.setDistance(geoResult.getDistance().getValue()); // Add to response DTO
                                nearbyShippers.add(location);
                            }
                        }
                        
                    } catch (NumberFormatException e) {
                        log.warn("Invalid shipper ID in GEO results: {}", geoResult.getContent().getName());
                    }
                }
            }
            
            log.info("🔍 Found {} online shippers within {}km from ({}, {}) using Redis GEO", 
                    nearbyShippers.size(), radiusKm, centerLat, centerLng);
                    
        } catch (Exception e) {
            log.error("💥 Error finding shippers with Redis GEO: {}", e.getMessage(), e);
        }
        
        return nearbyShippers;
    }

    /**
     * ✅ Get distance between two shippers using Redis GEODIST
     */
    public Double getDistanceBetweenShippers(Long shipperId1, Long shipperId2) {
        try {
            GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
            
            Distance distance = geoOps.distance(GEO_KEY, 
                shipperId1.toString(), 
                shipperId2.toString(), 
                Metrics.KILOMETERS);
                
            return distance != null ? distance.getValue() : null;
            
        } catch (Exception e) {
            log.error("💥 Error calculating distance between shippers: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * ✅ Get shipper coordinates using Redis GEOPOS
     */
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

    /**
     * Get all online shippers (optimized with Redis SET)
     */
    public List<ShipperLocationResponse> getAllOnlineShippers() {
        List<ShipperLocationResponse> onlineShippers = new ArrayList<>();
        
        try {
            // ✅ Get online shipper IDs from Redis SET (faster than scanning all keys)
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

    /**
     * Remove shipper from cache (both detailed and GEO)
     */
    public void removeShipperLocationCache(Long shipperId) {
        try {
            // Remove detailed data
            String detailKey = RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + shipperId;
            redisTemplate.delete(detailKey);
            
            // ✅ Remove from GEO set
            GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
            geoOps.remove(GEO_KEY, shipperId.toString());
            
            // Remove from online set
            redisTemplate.opsForSet().remove(ONLINE_SHIPPERS_SET, shipperId.toString());
            
            log.debug("🗑️ Removed shipper {} from all Redis caches", shipperId);
            
        } catch (Exception e) {
            log.error("💥 Error removing shipper from cache: {}", e.getMessage(), e);
        }
    }

    // Health check methods
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
}
