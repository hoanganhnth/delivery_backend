package com.delivery.tracking_service.service;

import com.delivery.tracking_service.dto.event.LocationFanoutEnvelope;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperDeliveryAssignmentStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LocationFanoutPublisher {

    public static final String CHANNEL = "tracking:location-fanout";
    private final StringRedisTemplate redis;
    private final ShipperDeliveryAssignmentStore assignments;
    private final ObjectMapper objectMapper;

    public LocationFanoutPublisher(StringRedisTemplate redis,
                                   ShipperDeliveryAssignmentStore assignments,
                                   ObjectMapper objectMapper) {
        this.redis = redis;
        this.assignments = assignments;
        this.objectMapper = objectMapper;
    }

    public void publish(ShipperLocationResponse location) {
        if (location == null || location.getShipperId() == null) return;
        assignments.activeDelivery(location.getShipperId()).ifPresent(deliveryId -> {
            try {
                redis.convertAndSend(CHANNEL,
                        objectMapper.writeValueAsString(new LocationFanoutEnvelope(deliveryId, location)));
            } catch (Exception exception) {
                // Redis GEO already retains the latest location and subscribe sends
                // that value, so transient Pub/Sub loss cannot lose the final state.
                log.warn("Cannot publish realtime fanout for shipper {}; subscriber will recover from Redis",
                        location.getShipperId(), exception);
            }
        });
    }
}
