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

    private RedisGeoRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RedisGeoRepository(redisTemplate, org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class));
        when(redisTemplate.opsForValue()).thenReturn(values);
    }

    @Test
    void pairedCacheKeepsAbsoluteMetadataAndPublicDetailsTogether() {
        var location = new ShipperLocationResponse();
        location.setShipperId(7L);
        var stored = new StoredShipperLocation(location, 12345L);
        when(values.get("shipper:location:7")).thenReturn(stored);
        org.assertj.core.api.Assertions.assertThat(repository.getCachedProjection(7L)).isEqualTo(stored);
        org.assertj.core.api.Assertions.assertThat(repository.getCachedShipperLocation(7L)).isSameAs(location);
    }

    @Test
    void coordinateFreeOfflineStillKeepsOrderingMetadata() {
        var stored = new StoredShipperLocation(null, 12345L);
        when(values.get("shipper:location:7")).thenReturn(stored);
        org.assertj.core.api.Assertions.assertThat(repository.getCachedProjection(7L)).isEqualTo(stored);
        org.assertj.core.api.Assertions.assertThat(repository.getCachedShipperLocation(7L)).isNull();
    }

    @Test
    void legacyDetailsRemainReadableButCannotSeedAnAbsoluteWatermark() {
        var location = new ShipperLocationResponse();
        when(values.get("shipper:location:7")).thenReturn(location);
        org.assertj.core.api.Assertions.assertThat(repository.getCachedShipperLocation(7L)).isSameAs(location);
        assertThatThrownBy(() -> repository.getCachedProjection(7L))
                .hasMessage("Cached location has no absolute ordering metadata");
    }

    @Test
    void redisReadFailureIsNotReportedAsMissingLocation() {
        when(values.get("shipper:location:7")).thenThrow(new RuntimeException("redis unavailable"));

        assertThatThrownBy(() -> repository.getCachedShipperLocation(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot read shipper location from Redis");
    }
}
