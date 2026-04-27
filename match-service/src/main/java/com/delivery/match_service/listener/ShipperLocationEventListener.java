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

            matchRedisGeoRepository.addOrUpdateShipperLocation(shipperId, latitude, longitude, isOnline);

            log.debug("📍 Replicated shipper {} location to local Geo", shipperId);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("💥 Error processing shipper location event: {}", e.getMessage());
            acknowledgment.acknowledge(); // Ack để không replay lỗi liên tục
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

            log.info("📥 [MatchGeo] Received shipper status: shipper={}, status={}", shipperId, status);

            if ("BUSY".equalsIgnoreCase(status)) {
                matchRedisGeoRepository.markShipperBusy(shipperId);
            } else if ("AVAILABLE".equalsIgnoreCase(status)) {
                matchRedisGeoRepository.markShipperAvailable(shipperId);
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("💥 Error processing shipper status change: {}", e.getMessage());
            acknowledgment.acknowledge();
        }
    }
}
