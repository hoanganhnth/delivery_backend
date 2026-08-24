package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.entity.RestaurantServiceabilityZone;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.repository.RestaurantServiceabilityZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceabilityServiceTest {

    private static final String OUTER = """
            {"type":"Polygon","coordinates":[[[106.60,10.70],[106.70,10.70],
            [106.70,10.80],[106.60,10.80],[106.60,10.70]]]}""";
    private static final String INNER = """
            {"type":"Polygon","coordinates":[[[106.62,10.72],[106.68,10.72],
            [106.68,10.78],[106.62,10.78],[106.62,10.72]]]}""";

    @Mock RestaurantRepository restaurantRepository;
    @Mock RestaurantServiceabilityZoneRepository zoneRepository;

    private RestaurantServiceabilityService service;

    @BeforeEach
    void setUp() {
        service = new RestaurantServiceabilityService(restaurantRepository, zoneRepository);
        ReflectionTestUtils.setField(service, "enabled", true);
        lenient().when(restaurantRepository.existsById(7L)).thenReturn(true);
    }

    @Test
    void overlappingZonesUsePriorityThenIdAndBoundaryIsIncluded() {
        RestaurantServiceabilityZone low = zone(11L, 1, OUTER);
        RestaurantServiceabilityZone high = zone(12L, 2, INNER);
        when(zoneRepository.findByRestaurantIdOrderByPriorityDescIdAsc(7L))
                .thenReturn(List.of(high, low));

        var decision = service.evaluate(7L, 10.75, 106.65);

        assertThat(decision.enabled()).isTrue();
        assertThat(decision.serviceable()).isTrue();
        assertThat(decision.zoneId()).isEqualTo(12L);
        assertThat(service.evaluate(7L, 10.70, 106.65).serviceable()).isTrue();
    }

    @Test
    void malformedPersistedZoneFailsClosed() {
        when(zoneRepository.findByRestaurantIdOrderByPriorityDescIdAsc(7L))
                .thenReturn(List.of(zone(11L, 1, "not-json")));

        var decision = service.evaluate(7L, 10.75, 106.65);

        assertThat(decision.serviceable()).isFalse();
        assertThat(decision.reason()).isEqualTo("INVALID_ZONE_CONFIGURATION");
    }

    @Test
    void disabledCapabilityDoesNotReadZoneData() {
        ReflectionTestUtils.setField(service, "enabled", false);

        var decision = service.evaluate(7L, 10.75, 106.65);

        assertThat(decision.enabled()).isFalse();
        assertThat(decision.serviceable()).isFalse();
    }

    private RestaurantServiceabilityZone zone(Long id, int priority, String polygon) {
        RestaurantServiceabilityZone zone = new RestaurantServiceabilityZone();
        zone.setId(id);
        zone.setRestaurantId(7L);
        zone.setPriority(priority);
        zone.setPolygonGeoJson(polygon);
        zone.setRevision(3L);
        zone.setActive(true);
        return zone;
    }
}
