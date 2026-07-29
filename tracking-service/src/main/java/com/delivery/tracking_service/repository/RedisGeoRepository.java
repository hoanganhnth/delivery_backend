
package com.delivery.tracking_service.repository;
import org.springframework.stereotype.Repository;

import com.delivery.tracking_service.common.constants.RedisConstants;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;

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
            GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
            if (Boolean.TRUE.equals(location.getIsOnline())
                    && location.getLatitude() != null && location.getLongitude() != null) {
                Point point = new Point(location.getLongitude(), location.getLatitude());
                geoOps.add(GEO_KEY, point, shipperId.toString());
                redisTemplate.expire(GEO_KEY, RedisConstants.SHIPPER_LOCATION_TTL, TimeUnit.SECONDS);
                log.debug("✅ Cached GEO location for shipper: {} at ({}, {})", shipperId, location.getLatitude(), location.getLongitude());
            } else if (!Boolean.TRUE.equals(location.getIsOnline())) {
                geoOps.remove(GEO_KEY, shipperId.toString());
            }
            if (Boolean.TRUE.equals(location.getIsOnline())) {
                redisTemplate.opsForSet().add(ONLINE_SHIPPERS_SET, shipperId.toString());
                redisTemplate.expire(ONLINE_SHIPPERS_SET, RedisConstants.SHIPPER_LOCATION_TTL, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForSet().remove(ONLINE_SHIPPERS_SET, shipperId.toString());
            }
        } catch (Exception e) {
            log.error("💥 Error caching shipper location with GEO: {}", e.getMessage(), e);
            throw new IllegalStateException("Cannot persist shipper location in Redis", e);
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
            throw new IllegalStateException("Cannot read shipper location from Redis", e);
        }
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
            throw new IllegalStateException("Cannot remove shipper location from Redis", e);
        }
    }

}
