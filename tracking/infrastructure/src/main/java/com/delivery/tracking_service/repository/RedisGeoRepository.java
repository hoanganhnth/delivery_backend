
package com.delivery.tracking_service.repository;
import org.springframework.stereotype.Repository;

import com.delivery.tracking_service.common.constants.RedisConstants;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import com.delivery.tracking.domain.PublisherExpiryClaim;
import com.delivery.tracking.domain.PublisherLease;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisGeoRepository implements ShipperLocationRepository {
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private static final String GEO_KEY = "shippers:geo:locations";
    private static final String ONLINE_SHIPPERS_SET = "shippers:online:set";
    private static final DefaultRedisScript<Long> CACHE_IF_CURRENT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] or redis.call('GET', KEYS[2]) ~= ARGV[2] then
              return 0
            end
            local geoType = redis.call('TYPE', KEYS[4]).ok
            local onlineType = redis.call('TYPE', KEYS[5]).ok
            if (geoType ~= 'none' and geoType ~= 'zset') or (onlineType ~= 'none' and onlineType ~= 'set') then
              return redis.error_reply('Invalid shipper membership type')
            end
            if ARGV[5] == '1' then
              -- GEO validates Redis latitude limits before any projection is changed.
              redis.call('GEOADD', KEYS[4], ARGV[6], ARGV[7], ARGV[4])
              redis.call('EXPIRE', KEYS[4], ARGV[8])
              redis.call('SADD', KEYS[5], ARGV[4])
              redis.call('EXPIRE', KEYS[5], ARGV[8])
            else
              redis.call('ZREM', KEYS[4], ARGV[4])
              redis.call('SREM', KEYS[5], ARGV[4])
            end
            redis.call('SET', KEYS[3], ARGV[3], 'EX', ARGV[8])
            return 1
            """, Long.class);

    @Override
    public boolean cacheIfCurrentPublisher(PublisherLease lease, ShipperLocationResponse location) {
        String id = Long.toString(lease.shipperId());
        Long result = stringRedisTemplate.execute(CACHE_IF_CURRENT, List.of(
                ShipperPublisherLeaseRepository.GENERATION_PREFIX + id,
                ShipperPublisherLeaseRepository.ACTIVE_PREFIX + id,
                RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + id, GEO_KEY, ONLINE_SHIPPERS_SET),
                Long.toString(lease.generation()), lease.redisValue(), serialized(location), serialized(id),
                Boolean.TRUE.equals(location.getIsOnline()) ? "1" : "0",
                String.valueOf(location.getLongitude()), String.valueOf(location.getLatitude()),
                Long.toString(RedisConstants.SHIPPER_LOCATION_TTL));
        return Long.valueOf(1).equals(result);
    }
    private static final DefaultRedisScript<Long> OFFLINE_IF_EXPIRED = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] or redis.call('EXISTS', KEYS[2]) == 1 then
              return 0
            end
            local score = redis.call('ZSCORE', KEYS[3], ARGV[2])
            local now = redis.call('TIME')
            if not score or tonumber(score) ~= tonumber(ARGV[3])
                or tonumber(score) <= tonumber(now[1]) * 1000 + tonumber(now[2]) / 1000 then
              return 0
            end
            -- Redis does not roll back earlier Lua writes after a command error.
            -- Validate membership types before changing any of the three projections.
            local geoType = redis.call('TYPE', KEYS[5]).ok
            local onlineType = redis.call('TYPE', KEYS[6]).ok
            if (geoType ~= 'none' and geoType ~= 'zset') or (onlineType ~= 'none' and onlineType ~= 'set') then
              return redis.error_reply('Invalid shipper membership type')
            end
            if ARGV[4] == '1' then
              redis.call('SET', KEYS[4], ARGV[5], 'EX', ARGV[7])
            else
              redis.call('DEL', KEYS[4])
            end
            redis.call('ZREM', KEYS[5], ARGV[6])
            redis.call('SREM', KEYS[6], ARGV[6])
            return 1
            """, Long.class);

    @Override
    public boolean applyOfflineIfExpired(PublisherExpiryClaim claim, Optional<ShipperLocationResponse> cachedOffline) {
        var lease = claim.lease();
        String id = Long.toString(lease.shipperId());
        // The normal cache/GEO/set API uses GenericJackson serialization; Lua uses raw UTF-8.
        String payload = cachedOffline.map(this::serialized).orElse("");
        Long result = stringRedisTemplate.execute(OFFLINE_IF_EXPIRED, List.of(
                ShipperPublisherLeaseRepository.GENERATION_PREFIX + id,
                ShipperPublisherLeaseRepository.ACTIVE_PREFIX + id,
                ShipperPublisherLeaseRepository.DEADLINES_KEY,
                RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + id, GEO_KEY, ONLINE_SHIPPERS_SET),
                Long.toString(lease.generation()), id + ":" + lease.redisValue(),
                Long.toString(claim.claimUntilEpochMillis()), cachedOffline.isPresent() ? "1" : "0",
                payload, serialized(id), Long.toString(RedisConstants.SHIPPER_LOCATION_TTL));
        return Long.valueOf(1).equals(result);
    }

    @SuppressWarnings("unchecked")
    private String serialized(Object value) {
        var serializer = (org.springframework.data.redis.serializer.RedisSerializer<Object>) redisTemplate.getValueSerializer();
        return new String(serializer.serialize(value), StandardCharsets.UTF_8);
    }

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
                log.debug("✅ Cached GEO location for shipper {}", shipperId);
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
