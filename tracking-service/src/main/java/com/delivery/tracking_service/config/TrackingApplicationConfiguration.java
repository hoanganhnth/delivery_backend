package com.delivery.tracking_service.config;

import com.delivery.tracking.application.DefaultTrackingService;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Composes the framework-free tracking use case with service adapters. */
@Configuration
public class TrackingApplicationConfiguration {

    @Bean
    ShipperIdentityUseCase shipperIdentityUseCase(ShipperIdentityReadPort identities) {
        return new DefaultShipperIdentityUseCase(identities);
    }


    @Bean
    LocationStorePort trackingLocationStore(ShipperLocationRepository repository) {
        return new LocationStorePort() {
            @Override
            public void save(com.delivery.tracking.domain.LocationSnapshot location) {
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
            }

            @Override
            public java.util.Optional<com.delivery.tracking.domain.LocationSnapshot> findByShipperId(long shipperId) {
                var response = repository.getCachedShipperLocation(shipperId);
                if (response == null || response.getLatitude() == null || response.getLongitude() == null) {
                    return java.util.Optional.empty();
                }
                Instant updated = parse(response.getUpdatedAt());
                Instant ping = parse(response.getLastPing());
                return java.util.Optional.of(new com.delivery.tracking.domain.LocationSnapshot(shipperId,
                        new com.delivery.tracking.domain.Coordinate(response.getLatitude(), response.getLongitude()),
                        response.getAccuracy(), response.getSpeed(), response.getHeading(),
                        Boolean.TRUE.equals(response.getIsOnline()), ping, updated));
            }

            @Override
            public void remove(long shipperId) {
                repository.removeShipperLocationCache(shipperId);
            }

            private Instant parse(String value) {
                if (value == null || value.isBlank()) return Instant.now();
                try { return Instant.parse(value); }
                catch (RuntimeException ignored) { return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC); }
            }
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
