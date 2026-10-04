
package com.delivery.tracking_service.repository;
import org.springframework.stereotype.Repository;

import com.delivery.tracking_service.common.constants.RedisConstants;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import com.delivery.tracking.domain.PublisherExpiryClaim;
import com.delivery.tracking.domain.PublisherLease;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;


@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisGeoRepository implements ShipperLocationRepository {
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private static final String GEO_KEY = "shippers:geo:locations";
    private static final String ONLINE_SHIPPERS_SET = "shippers:online:set";
    // Compare the absolute metadata in the same value/transaction as the projection writes.
    private static final String PROJECTION_ORDER = """
            local function projectionIsNewer(key, incoming)
              local raw = redis.call('GET', key)
              if not raw then return true end
              local current = cjson.decode(raw)
              if type(current) ~= 'table' then error('Invalid shipper location projection') end
              -- A recognized legacy DTO has no absolute metadata; the new writer replaces it.
              if current['@class'] == 'com.delivery.tracking_service.dto.response.ShipperLocationResponse' then
                return true
              end
              local timestamp = current.occurredAt
              if current['@class'] ~= 'com.delivery.tracking_service.repository.StoredShipperLocation'
                  or type(timestamp) ~= 'number' or timestamp <= 0 or timestamp ~= math.floor(timestamp) then
                error('Invalid shipper location ordering metadata')
              end
              return incoming > timestamp
            end
            """;
    private static final DefaultRedisScript<Long> CACHE_IF_CURRENT = new DefaultRedisScript<>(PROJECTION_ORDER + """
            if ARGV[9] == '1' and (redis.call('GET', KEYS[1]) ~= ARGV[1] or redis.call('GET', KEYS[2]) ~= ARGV[2]) then
              return 0
            end
            local geoType = redis.call('TYPE', KEYS[4]).ok
            local onlineType = redis.call('TYPE', KEYS[5]).ok
            if (geoType ~= 'none' and geoType ~= 'zset') or (onlineType ~= 'none' and onlineType ~= 'set') then
              return redis.error_reply('Invalid shipper membership type')
            end
            if not projectionIsNewer(KEYS[3], tonumber(ARGV[11])) then return 2 end
            if ARGV[5] == '1' then
              -- GEO validates Redis latitude limits before any projection is changed.
              if ARGV[10] == '1' then
                redis.call('GEOADD', KEYS[4], ARGV[6], ARGV[7], ARGV[4])
                redis.call('EXPIRE', KEYS[4], ARGV[8])
              end
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
    public boolean cacheIfCurrentPublisher(PublisherLease lease, ShipperLocationResponse location, long occurredAt) {
        return writeLocation(lease.shipperId(), location, occurredAt, lease);
    }

    private boolean writeLocation(Long shipperId, ShipperLocationResponse location, long occurredAt, PublisherLease lease) {
        String id = shipperId.toString();
        Long result = stringRedisTemplate.execute(CACHE_IF_CURRENT, List.of(
                ShipperPublisherLeaseRepository.GENERATION_PREFIX + id,
                ShipperPublisherLeaseRepository.ACTIVE_PREFIX + id,
                RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + id, GEO_KEY, ONLINE_SHIPPERS_SET),
                lease == null ? "0" : Long.toString(lease.generation()), lease == null ? "" : lease.redisValue(),
                serialized(new StoredShipperLocation(location, occurredAt)), serialized(id),
                location != null && Boolean.TRUE.equals(location.getIsOnline()) ? "1" : "0",
                location == null ? "" : String.valueOf(location.getLongitude()),
                location == null ? "" : String.valueOf(location.getLatitude()),
                Long.toString(RedisConstants.SHIPPER_LOCATION_TTL), lease == null ? "0" : "1",
                location != null && location.getLongitude() != null && location.getLatitude() != null ? "1" : "0", Long.toString(occurredAt));
        // 2 is an admitted stale fact: projection no-op, preserving ACK/publication contracts.
        return Long.valueOf(1).equals(result) || Long.valueOf(2).equals(result);
    }
    private static final DefaultRedisScript<Long> OFFLINE_IF_EXPIRED = new DefaultRedisScript<>(PROJECTION_ORDER + """
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
            if not projectionIsNewer(KEYS[4], tonumber(ARGV[7])) then return 2 end
            redis.call('ZREM', KEYS[5], ARGV[5])
            redis.call('SREM', KEYS[6], ARGV[5])
            redis.call('SET', KEYS[4], ARGV[4], 'EX', ARGV[6])
            return 1
            """, Long.class);

    @Override
    public boolean applyOfflineIfExpired(PublisherExpiryClaim claim, Optional<ShipperLocationResponse> cachedOffline, long occurredAt) {
        var lease = claim.lease();
        String id = Long.toString(lease.shipperId());
        // The normal cache/GEO/set API uses GenericJackson serialization; Lua uses raw UTF-8.
        String payload = serialized(new StoredShipperLocation(cachedOffline.orElse(null), occurredAt));
        Long result = stringRedisTemplate.execute(OFFLINE_IF_EXPIRED, List.of(
                ShipperPublisherLeaseRepository.GENERATION_PREFIX + id,
                ShipperPublisherLeaseRepository.ACTIVE_PREFIX + id,
                ShipperPublisherLeaseRepository.DEADLINES_KEY,
                RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + id, GEO_KEY, ONLINE_SHIPPERS_SET),
                Long.toString(lease.generation()), id + ":" + lease.redisValue(),
                Long.toString(claim.claimUntilEpochMillis()),
                payload, serialized(id), Long.toString(RedisConstants.SHIPPER_LOCATION_TTL), Long.toString(occurredAt));
        // 2 is an admitted stale fact: projection no-op, preserving ACK/publication contracts.
        return Long.valueOf(1).equals(result) || Long.valueOf(2).equals(result);
    }

    @SuppressWarnings("unchecked")
    private String serialized(Object value) {
        var serializer = (org.springframework.data.redis.serializer.RedisSerializer<Object>) redisTemplate.getValueSerializer();
        return new String(serializer.serialize(value), StandardCharsets.UTF_8);
    }

    @Override
    public void cacheShipperLocation(Long shipperId, ShipperLocationResponse location, long occurredAt) {
        try {
            if (!writeLocation(shipperId, location, occurredAt, null)) throw new IllegalStateException("Redis did not store location");
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot persist shipper location in Redis", failure);
        }
    }

    @Override
    public ShipperLocationResponse getCachedShipperLocation(Long shipperId) {
        Object cached = readCache(shipperId);
        if (cached instanceof StoredShipperLocation projection) return projection.location();
        // Retain legacy coordinates for an explicit offline transition; never invent ordering metadata.
        return cached instanceof ShipperLocationResponse row ? row : null;
    }

    public StoredShipperLocation getCachedProjection(Long shipperId) {
        Object cached = readCache(shipperId);
        if (cached == null) return null;
        if (cached instanceof StoredShipperLocation projection) return projection;
        throw new IllegalStateException("Cached location has no absolute ordering metadata");
    }

    private Object readCache(Long shipperId) {
        try { return redisTemplate.opsForValue().get(RedisConstants.SHIPPER_LOCATION_KEY_PREFIX + shipperId); }
        catch (Exception failure) { throw new IllegalStateException("Cannot read shipper location from Redis", failure); }
    }

    @Override
    public void removeShipperLocationCache(Long shipperId, long occurredAt) {
        // Coordinate-free offline retains a timestamp marker so a new subscription rejects old packets.
        cacheShipperLocation(shipperId, null, occurredAt);
    }
}
