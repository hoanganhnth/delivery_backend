package com.delivery.tracking_service.repository;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisGeoRepositoryTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> values;
    @Mock SetOperations<String, Object> sets;
    @Mock GeoOperations<String, Object> geo;

    private RedisGeoRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RedisGeoRepository(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(values);
    }

    @Test
    void cachingOfflineLocationRemovesGeoAndOnlineMembership() {
        when(redisTemplate.opsForGeo()).thenReturn(geo);
        when(redisTemplate.opsForSet()).thenReturn(sets);
        ShipperLocationResponse location = new ShipperLocationResponse();
        location.setShipperId(7L);
        location.setLatitude(10.77);
        location.setLongitude(106.70);
        location.setIsOnline(false);

        repository.cacheShipperLocation(7L, location);

        verify(values).set("shipper:location:7", location, 300L, TimeUnit.SECONDS);
        verify(geo).remove("shippers:geo:locations", "7");
        verify(sets).remove("shippers:online:set", "7");
    }

    @Test
    void redisReadFailureIsNotReportedAsMissingLocation() {
        when(values.get("shipper:location:7")).thenThrow(new RuntimeException("redis unavailable"));

        assertThatThrownBy(() -> repository.getCachedShipperLocation(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot read shipper location from Redis");
    }
}
