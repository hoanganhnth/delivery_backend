package com.delivery.match_service.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ✅ Local Redis Geo Repository cho Match Service
 * Lưu bản sao vị trí shipper nhận từ tracking-service qua Kafka
 * Dùng key riêng "match:shippers:geo" để tách biệt với tracking-service
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class MatchRedisGeoRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String GEO_KEY = "match:shippers:geo";
    private static final String ONLINE_SET_KEY = "match:shippers:online";
    private static final String BUSY_PREFIX = "match:shipper:busy:";
    private static final long LOCATION_TTL_SECONDS = 300; // 5 phút — nếu không nhận update thì coi như cũ

    /**
     * ✅ Thêm/cập nhật vị trí shipper vào Redis Geo local
     */
    public void addOrUpdateShipperLocation(Long shipperId, Double latitude, Double longitude, Boolean isOnline) {
        try {
            if (latitude == null || longitude == null) return;

            GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
            Point point = new Point(longitude, latitude);
            geoOps.add(GEO_KEY, point, shipperId.toString());

            // Quản lý online set
            if (Boolean.TRUE.equals(isOnline)) {
                redisTemplate.opsForSet().add(ONLINE_SET_KEY, shipperId.toString());
            } else {
                redisTemplate.opsForSet().remove(ONLINE_SET_KEY, shipperId.toString());
                // Nếu offline, xoá khỏi geo luôn
                geoOps.remove(GEO_KEY, shipperId.toString());
            }

            log.debug("📍 [MatchGeo] Updated shipper {} at ({}, {}) online={}", shipperId, latitude, longitude, isOnline);

        } catch (Exception e) {
            log.error("💥 [MatchGeo] Error updating shipper {}: {}", shipperId, e.getMessage());
        }
    }

    /**
     * ✅ Tìm shipper gần trong bán kính — query 100% local, không gọi REST
     */
    public List<NearbyShipperResult> findNearbyShippers(Double lat, Double lng, Double radiusKm, Integer limit) {
        List<NearbyShipperResult> results = new ArrayList<>();
        try {
            GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
            Point center = new Point(lng, lat);
            Distance radius = new Distance(radiusKm, Metrics.KILOMETERS);
            Circle circle = new Circle(center, radius);

            RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                    .newGeoRadiusArgs()
                    .includeDistance()
                    .includeCoordinates()
                    .sortAscending()
                    .limit(limit != null ? limit * 3 : 30); // Lấy nhiều hơn vì sẽ filter busy/offline

            GeoResults<RedisGeoCommands.GeoLocation<Object>> geoResults = geoOps.radius(GEO_KEY, circle, args);

            if (geoResults != null && geoResults.getContent() != null) {
                int count = 0;
                int maxResults = limit != null ? limit : 10;

                for (var geoResult : geoResults.getContent()) {
                    if (count >= maxResults) break;

                    String shipperIdStr = geoResult.getContent().getName().toString();

                    // Filter: chỉ lấy online + không busy
                    boolean isOnline = Boolean.TRUE.equals(
                            redisTemplate.opsForSet().isMember(ONLINE_SET_KEY, shipperIdStr));
                    boolean isBusy = Boolean.TRUE.equals(
                            redisTemplate.hasKey(BUSY_PREFIX + shipperIdStr));

                    if (isOnline && !isBusy) {
                        Long shipperId = Long.parseLong(shipperIdStr);
                        Point coords = geoResult.getContent().getPoint();
                        double distance = geoResult.getDistance().getValue();

                        results.add(new NearbyShipperResult(
                                shipperId, coords.getY(), coords.getX(), distance));
                        count++;
                    }
                }
            }

            log.info("🔍 [MatchGeo] Found {} available shippers within {}km from ({}, {})",
                    results.size(), radiusKm, lat, lng);

        } catch (Exception e) {
            log.error("💥 [MatchGeo] Error finding nearby shippers: {}", e.getMessage());
        }
        return results;
    }

    /**
     * ✅ Đánh dấu shipper bận
     */
    public void markShipperBusy(Long shipperId) {
        try {
            redisTemplate.opsForValue().set(BUSY_PREFIX + shipperId.toString(), "BUSY", 2, TimeUnit.HOURS);
            log.info("🔴 [MatchGeo] Marked shipper {} as BUSY", shipperId);
        } catch (Exception e) {
            log.error("💥 [MatchGeo] Error marking shipper {} busy: {}", shipperId, e.getMessage());
        }
    }

    /**
     * ✅ Đánh dấu shipper rảnh
     */
    public void markShipperAvailable(Long shipperId) {
        try {
            redisTemplate.delete(BUSY_PREFIX + shipperId.toString());
            log.info("🟢 [MatchGeo] Marked shipper {} as AVAILABLE", shipperId);
        } catch (Exception e) {
            log.error("💥 [MatchGeo] Error marking shipper {} available: {}", shipperId, e.getMessage());
        }
    }

    /**
     * ✅ Data class cho kết quả tìm kiếm
     */
    public static class NearbyShipperResult {
        public final Long shipperId;
        public final Double latitude;
        public final Double longitude;
        public final Double distanceKm;

        public NearbyShipperResult(Long shipperId, Double latitude, Double longitude, Double distanceKm) {
            this.shipperId = shipperId;
            this.latitude = latitude;
            this.longitude = longitude;
            this.distanceKm = distanceKm;
        }
    }
}
