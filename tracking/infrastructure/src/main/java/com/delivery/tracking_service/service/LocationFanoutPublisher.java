package com.delivery.tracking_service.service;
import com.delivery.tracking.application.api.LocationFanoutUseCase;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import org.springframework.stereotype.Service;
/** DTO translator to actual application policy; distributed transport is a separate adapter. */
@Service
public class LocationFanoutPublisher {
    public static final String CHANNEL = "tracking:location-fanout";
    private final LocationFanoutUseCase fanout;
    public LocationFanoutPublisher(LocationFanoutUseCase fanout) { this.fanout = fanout; }
    public void publish(ShipperLocationResponse location, long occurredAt) { fanout.publish(FanoutLocationMapper.from(location, occurredAt)); }
}
