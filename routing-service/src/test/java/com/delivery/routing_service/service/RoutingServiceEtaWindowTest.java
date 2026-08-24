package com.delivery.routing_service.service;

import com.delivery.routing_service.api.Coordinate;
import com.delivery.routing_service.api.EtaWindowRequest;
import com.delivery.routing_service.config.RoutingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingServiceEtaWindowTest {

    @Test
    void etaWindowAddsPrepAndTenMinuteBoundToDrivingFallback() {
        RoutingProperties properties = new RoutingProperties();
        properties.setFallbackSpeedKmh(18);
        RoutingService service = new RoutingService(properties, new ObjectMapper());

        var response = service.etaWindow(new EtaWindowRequest(
                new Coordinate(10.76, 106.66),
                new Coordinate(10.78, 106.68),
                15));

        assertThat(response.minMinutes()).isGreaterThan(15);
        assertThat(response.maxMinutes()).isEqualTo(response.minMinutes() + 10);
        assertThat(response.source()).isEqualTo("GEODESIC_FALLBACK");
    }
}
