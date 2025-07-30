package com.delivery.tracking_service.service;

import com.delivery.tracking_service.common.constants.RedisConstants;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Shipper Location Cache - Core functionality
    public void cacheShipperLocation(Long shipperId, ShipperLocationResponse location) {
        String key = RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + shipperId;
        redisTemplate.opsForValue().set(key, location, RedisConstants.SHIPPER_LOCATION_TTL, TimeUnit.SECONDS);
        log.debug("Cached location for shipper: {} at lat: {}, lng: {}", 
            shipperId, location.getLatitude(), location.getLongitude());
    }

    public ShipperLocationResponse getCachedShipperLocation(Long shipperId) {
        String key = RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + shipperId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof ShipperLocationResponse) {
            log.debug("Retrieved cached location for shipper: {}", shipperId);
            return (ShipperLocationResponse) cached;
        }
        log.debug("No cached location found for shipper: {}", shipperId);
        return null;
    }

    public void removeShipperLocationCache(Long shipperId) {
        String key = RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + shipperId;
        redisTemplate.delete(key);
        log.debug("Removed cached location for shipper: {}", shipperId);
    }

    // Get all online shippers for match service
    public List<ShipperLocationResponse> getAllOnlineShippers() {
        List<ShipperLocationResponse> onlineShippers = new ArrayList<>();
        
        try {
            String pattern = RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    Object cached = redisTemplate.opsForValue().get(key);
                    if (cached instanceof ShipperLocationResponse) {
                        ShipperLocationResponse location = (ShipperLocationResponse) cached;
                        if (Boolean.TRUE.equals(location.getIsOnline())) {
                            onlineShippers.add(location);
                        }
                    }
                }
            }
            
            log.debug("Retrieved {} online shippers from cache", onlineShippers.size());
        } catch (Exception e) {
            log.error("Error retrieving online shippers from Redis", e);
        }
        
        return onlineShippers;
    }

    // Health check method
    public boolean isRedisAvailable() {
        try {
            redisTemplate.opsForValue().set("health:check", "OK", 5, TimeUnit.SECONDS);
            return "OK".equals(redisTemplate.opsForValue().get("health:check"));
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return false;
        }
    }
}
