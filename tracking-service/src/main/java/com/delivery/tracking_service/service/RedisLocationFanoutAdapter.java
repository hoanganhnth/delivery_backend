package com.delivery.tracking_service.service;
import com.delivery.tracking.application.api.*;
import com.delivery.tracking_service.dto.event.LocationFanoutEnvelope;
import com.delivery.tracking_service.repository.ShipperDeliveryAssignmentStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
@Component
@Slf4j
public class RedisLocationFanoutAdapter implements FanoutDeliveryReadPort, LocationFanoutEventPort {
    private final StringRedisTemplate redis;
    private final ShipperDeliveryAssignmentStore assignments;
    private final ObjectMapper mapper;
    public RedisLocationFanoutAdapter(StringRedisTemplate redis, ShipperDeliveryAssignmentStore assignments, ObjectMapper mapper) {
        this.redis = redis; this.assignments = assignments; this.mapper = mapper;
    }
    @Override public Set<Long> activeDeliveries(Long shipperId) { return assignments.activeDeliveries(shipperId); }
    @Override public Optional<Long> activeDelivery(Long shipperId) { return assignments.activeDelivery(shipperId); }
    @Override public void publish(Long deliveryId, FanoutLocation location) throws Exception {
        redis.convertAndSend(LocationFanoutPublisher.CHANNEL,
                mapper.writeValueAsString(new LocationFanoutEnvelope(deliveryId, FanoutLocationMapper.toResponse(location))));
    }
    @Override public void failed(Long shipperId, Exception failure) {
        log.warn("Cannot publish realtime fanout for shipper {}; subscriber will recover from Redis", shipperId, failure);
    }
}
