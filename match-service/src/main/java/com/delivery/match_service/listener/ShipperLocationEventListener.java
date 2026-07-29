package com.delivery.match_service.listener;

import com.delivery.match_service.repository.MatchRedisGeoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ✅ Kafka Listener để replicate vị trí shipper từ tracking-service
 * Consume 2 topics:
 *   - shipper.location-updated: cập nhật vị trí vào local Redis Geo
 *   - shipper.status-change: cập nhật busy/available flag
 */
@Slf4j
@Component
public class ShipperLocationEventListener {

    private static final long ONLINE_LOCATION_MAX_AGE_MILLIS = 300_000L;

    private final MatchRedisGeoRepository matchRedisGeoRepository;
    private final ObjectMapper objectMapper;

    public ShipperLocationEventListener(MatchRedisGeoRepository matchRedisGeoRepository) {
        this.matchRedisGeoRepository = matchRedisGeoRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * ✅ Consume vị trí shipper từ tracking-service → GEOADD vào local Redis
     */
    @KafkaListener(topics = "shipper.location-updated", groupId = "match-service")
    @SuppressWarnings("unchecked")
    public void handleShipperLocationUpdated(String message, Acknowledgment acknowledgment) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);

            Long shipperId = ((Number) event.get("shipperId")).longValue();
            Double latitude = event.get("latitude") != null ? ((Number) event.get("latitude")).doubleValue() : null;
            Double longitude = event.get("longitude") != null ? ((Number) event.get("longitude")).doubleValue() : null;
            Boolean isOnline = (Boolean) event.get("isOnline");
            long timestamp = event.get("timestamp") instanceof Number number
                    ? number.longValue() : 0L;
            if (shipperId <= 0 || timestamp <= 0 || isOnline == null) {
                throw new IllegalArgumentException("Invalid shipper location event");
            }

            if (Boolean.TRUE.equals(isOnline)) {
                if (latitude == null || !Double.isFinite(latitude) || latitude < -90 || latitude > 90
                        || longitude == null || !Double.isFinite(longitude)
                        || longitude < -180 || longitude > 180) {
                    throw new IllegalArgumentException("Online location event requires valid coordinates");
                }
                if (timestamp < System.currentTimeMillis() - ONLINE_LOCATION_MAX_AGE_MILLIS) {
                    log.info("Ignoring expired online location replay for shipper {} at {}",
                            shipperId, timestamp);
                    acknowledgment.acknowledge();
                    return;
                }
                matchRedisGeoRepository.addOrUpdateShipperLocation(
                        shipperId, latitude, longitude, true, timestamp);
            } else {
                matchRedisGeoRepository.markShipperOffline(shipperId, timestamp);
            }

            log.debug("📍 Replicated shipper {} location to local Geo", shipperId);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("💥 Error processing shipper location event: {}", e.getMessage());
            throw new IllegalStateException("Failed to process shipper location event", e);
        }
    }

    /**
     * ✅ Consume trạng thái busy/available từ delivery-service
     */
    @KafkaListener(topics = "shipper.status-change", groupId = "match-service")
    @SuppressWarnings("unchecked")
    public void handleShipperStatusChange(String message, Acknowledgment acknowledgment) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);

            Long shipperId = ((Number) event.get("shipperId")).longValue();
            String status = (String) event.get("status");
            Long deliveryId = event.get("deliveryId") instanceof Number number
                    ? number.longValue() : null;
            Long orderId = event.get("orderId") instanceof Number number
                    ? number.longValue() : null;
            long timestamp = event.get("timestamp") instanceof Number number
                    ? number.longValue() : 0L;
            String eventId = event.get("eventId") instanceof String value ? value : null;
            if (shipperId <= 0 || deliveryId == null || deliveryId <= 0
                    || orderId == null || orderId <= 0 || timestamp <= 0 || eventId == null) {
                throw new IllegalArgumentException(
                        "Stable eventId and positive shipper/delivery/order/timestamp are required");
            }
            java.util.UUID.fromString(eventId);
            String canonicalStatus = status == null
                    ? null : status.toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("BUSY", "AVAILABLE").contains(canonicalStatus)) {
                throw new IllegalArgumentException("Unsupported shipper status: " + status);
            }

            log.info("📥 [MatchGeo] Received shipper status: shipper={}, status={}", shipperId, status);

            matchRedisGeoRepository.applyShipperStatus(
                    shipperId, deliveryId, canonicalStatus, timestamp, eventId);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("💥 Error processing shipper status change: {}", e.getMessage());
            throw new IllegalStateException("Failed to process shipper status change", e);
        }
    }
}
