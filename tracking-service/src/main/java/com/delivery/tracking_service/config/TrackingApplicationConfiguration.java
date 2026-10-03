package com.delivery.tracking_service.config;

import com.delivery.tracking.application.DefaultTrackingService;
import com.delivery.tracking.application.DefaultShipperAvailabilityUseCase;
import com.delivery.tracking.application.api.ShipperAvailabilityUseCase;
import com.delivery.tracking.application.api.ShipperAvailabilityStorePort;
import com.delivery.tracking.application.api.ShipperAvailabilityEventPort;
import com.delivery.tracking.application.DefaultShipperIdentityUseCase;
import com.delivery.tracking.application.api.ShipperIdentityReadPort;
import com.delivery.tracking.application.api.ShipperIdentityUseCase;
import com.delivery.tracking.application.api.LocationEventPort;
import com.delivery.tracking.application.api.LocationStorePort;
import com.delivery.tracking.application.api.TrackingPort;
import com.delivery.tracking_service.repository.ShipperLocationRepository;
import com.delivery.tracking_service.service.ShipperLocationEventPublisher;
import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/** Composes the framework-free tracking use case with service adapters. */
@Configuration
public class TrackingApplicationConfiguration {

    @Bean
    ShipperAvailabilityUseCase shipperAvailabilityUseCase(ShipperAvailabilityStorePort store, ShipperAvailabilityEventPort events) {
        return new DefaultShipperAvailabilityUseCase(store, events);
    }


    @Bean
    ShipperIdentityUseCase shipperIdentityUseCase(ShipperIdentityReadPort identities) {
        return new DefaultShipperIdentityUseCase(identities);
    }


    @Bean
    LocationStorePort trackingLocationStore(ShipperLocationRepository repository) {
        return location -> {
            var response = new com.delivery.tracking_service.dto.response.ShipperLocationResponse();
            response.setShipperId(location.shipperId());
            response.setLatitude(location.coordinate().latitude());
            response.setLongitude(location.coordinate().longitude());
            response.setAccuracy(location.accuracy());
            response.setSpeed(location.speed());
            response.setHeading(location.heading());
            response.setIsOnline(location.online());
            response.setLastPing(location.lastPing().toString());
            response.setUpdatedAt(location.updatedAt().toString());
            repository.cacheShipperLocation(location.shipperId(), response);
        };
    }

    @Bean
    LocationEventPort trackingLocationEvents(ShipperLocationEventPublisher publisher,
                                             ShipperLocationWebSocketHandler webSocket) {
        return (location, source) -> {
            var response = new com.delivery.tracking_service.dto.response.ShipperLocationResponse();
            response.setShipperId(location.shipperId());
            response.setLatitude(location.coordinate().latitude());
            response.setLongitude(location.coordinate().longitude());
            response.setAccuracy(location.accuracy());
            response.setSpeed(location.speed());
            response.setHeading(location.heading());
            response.setIsOnline(location.online());
            response.setLastPing(location.lastPing().toString());
            response.setUpdatedAt(location.updatedAt().toString());
            webSocket.broadcastShipperLocation(response);
            publisher.publishLocationUpdate(response, source);
        };
    }

    @Bean
    TrackingPort tracking(LocationStorePort store, LocationEventPort events) {
        return new DefaultTrackingService(store, events);
    }
}
