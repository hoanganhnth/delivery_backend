package com.delivery.shipper_service.config;

import com.delivery.shipper.application.DefaultShipperProfileUseCases;
import com.delivery.shipper.application.DefaultShipperRatingUseCases;
import com.delivery.shipper.application.api.ShipperPorts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShipperUseCasesConfig {

    @Bean
    public DefaultShipperProfileUseCases defaultShipperProfileUseCases(
            ShipperPorts.ProfileStore profiles,
            ShipperPorts.TrackingAvailability tracking,
            ShipperPorts.IdentityStatusStore identity) {
        return new DefaultShipperProfileUseCases(profiles, tracking, identity);
    }

    @Bean
    public DefaultShipperRatingUseCases defaultShipperRatingUseCases(
            ShipperPorts.ProfileStore profiles,
            ShipperPorts.RatingStore ratings) {
        return new DefaultShipperRatingUseCases(profiles, ratings);
    }
}
