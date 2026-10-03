package com.delivery.tracking_service.config;

import com.delivery.tracking.application.DefaultTrackingService;
import com.delivery.tracking.application.DefaultPublisherSessionUseCase;
import com.delivery.tracking.application.api.PublisherSessionUseCase;
import com.delivery.tracking.application.api.PublisherLeaseStorePort;
import com.delivery.tracking.application.api.PublisherTaskSchedulePort;
import com.delivery.tracking.application.api.PublisherLeaseIncidentPort;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/** Composes the framework-free tracking use case with service adapters. */
@Configuration
public class TrackingApplicationConfiguration {

    @Bean
    PublisherSessionUseCase publisherSessionUseCase(PublisherLeaseStorePort leases, ShipperAvailabilityUseCase availability,
            PublisherTaskSchedulePort tasks, PublisherLeaseIncidentPort incidents,
            @Value("${app.websocket.publisher.disconnect-grace-seconds:30}") long graceSeconds,
            @Value("${app.websocket.publisher.lease-ttl-seconds:120}") long leaseSeconds,
            @Value("${app.websocket.publisher.expiry-claim-seconds:30}") long claimSeconds) {
        return new DefaultPublisherSessionUseCase(leases, availability, tasks, incidents, graceSeconds, leaseSeconds, claimSeconds);
    }


    @Bean
    ShipperAvailabilityUseCase shipperAvailabilityUseCase(ShipperAvailabilityStorePort store, ShipperAvailabilityEventPort events) {
        return new DefaultShipperAvailabilityUseCase(store, events);
    }


    @Bean
    ShipperIdentityUseCase shipperIdentityUseCase(ShipperIdentityReadPort identities) {
        return new DefaultShipperIdentityUseCase(identities);
    }


    @Bean
    com.delivery.tracking.application.api.ShipperIdentityInboxUseCase shipperIdentityInbox(
            com.delivery.tracking.application.api.ShipperIdentityInboxStorePort store) {
        return new com.delivery.tracking.application.DefaultShipperIdentityInboxUseCase(store);
    }

    @Bean
    com.delivery.tracking.application.api.LocationFanoutUseCase locationFanout(
            com.delivery.tracking.application.api.FanoutDeliveryReadPort assignments,
            com.delivery.tracking.application.api.LocationFanoutEventPort events) {
        return new com.delivery.tracking.application.DefaultLocationFanoutUseCase(assignments, events);
    }

    @Bean
    com.delivery.tracking.application.api.DeliveryRoomAssignmentUseCase deliveryRoomAssignments(
            com.delivery.tracking.application.api.DeliveryRoomAssignmentPort assignments,
            com.delivery.tracking.application.api.DeliveryRoomIndexPort rooms) {
        return new com.delivery.tracking.application.DefaultDeliveryRoomAssignmentUseCase(assignments, rooms);
    }

    @Bean
    com.delivery.tracking.application.api.DeliveryRoomSubscriptionUseCase deliveryRoomSubscriptions(
            com.delivery.tracking.application.api.DeliveryRoomAssignmentPort assignments,
            com.delivery.tracking.application.api.DeliveryRoomIndexPort rooms) {
        return new com.delivery.tracking.application.DefaultDeliveryRoomSubscriptionUseCase(assignments, rooms);
    }

    @Bean
    TrackingPort tracking(LocationStorePort store, LocationEventPort events) {
        return new DefaultTrackingService(store, events);
    }
}
