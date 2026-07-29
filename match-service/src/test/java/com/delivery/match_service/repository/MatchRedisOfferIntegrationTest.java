package com.delivery.match_service.repository;

import com.delivery.match_service.service.MatchCancellationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379"
})
@EnabledIfEnvironmentVariable(named = "MATCH_REDIS_INTEGRATION", matches = "true")
class MatchRedisOfferIntegrationTest {

    private static final long DELIVERY_ONE = 910001L;
    private static final long DELIVERY_TWO = 910002L;
    private static final long SHIPPER = 920001L;
    private static final long LOCATION_SHIPPER = 920002L;

    @Autowired MatchRedisGeoRepository repository;
    @Autowired MatchCancellationService cancellationService;
    @Autowired RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanFixtureKeys() {
        redisTemplate.delete("match:shipper:offer:" + SHIPPER);
        redisTemplate.delete("match:delivery:offer:" + DELIVERY_ONE);
        redisTemplate.delete("match:delivery:offer:" + DELIVERY_TWO);
        redisTemplate.delete("match:cancelled:" + DELIVERY_ONE);
        redisTemplate.delete("match:cancelled:" + DELIVERY_TWO);
        redisTemplate.opsForGeo().remove("match:shippers:geo", String.valueOf(LOCATION_SHIPPER));
        redisTemplate.opsForSet().remove("match:shippers:online", String.valueOf(LOCATION_SHIPPER));
        redisTemplate.delete("match:shipper:location-fresh:" + LOCATION_SHIPPER);
    }

    @Test
    void cancellationReleasesMatchingOfferAndFencesFutureReservation() {
        assertThat(repository.tryReserveShipperOffer(SHIPPER, DELIVERY_ONE, 180)).isTrue();
        assertThat(redisTemplate.opsForValue().get("match:shipper:offer:" + SHIPPER))
                .isEqualTo(String.valueOf(DELIVERY_ONE));
        assertThat(redisTemplate.opsForValue().get("match:delivery:offer:" + DELIVERY_ONE))
                .isEqualTo(String.valueOf(SHIPPER));

        cancellationService.markCancelled(DELIVERY_ONE);

        assertThat(redisTemplate.hasKey("match:shipper:offer:" + SHIPPER)).isFalse();
        assertThat(redisTemplate.hasKey("match:delivery:offer:" + DELIVERY_ONE)).isFalse();
        assertThat(repository.tryReserveShipperOffer(SHIPPER, DELIVERY_ONE, 180)).isFalse();
    }

    @Test
    void staleReleaseCannotDeleteANewerDeliveryOffer() {
        assertThat(repository.tryReserveShipperOffer(SHIPPER, DELIVERY_TWO, 180)).isTrue();

        assertThat(repository.releaseShipperOffer(SHIPPER, DELIVERY_ONE)).isFalse();

        assertThat(redisTemplate.opsForValue().get("match:shipper:offer:" + SHIPPER))
                .isEqualTo(String.valueOf(DELIVERY_TWO));
        assertThat(redisTemplate.opsForValue().get("match:delivery:offer:" + DELIVERY_TWO))
                .isEqualTo(String.valueOf(SHIPPER));
    }

    @Test
    void offlineTombstoneFencesOlderOnlineReplayInRealRedis() {
        long onlineTimestamp = System.currentTimeMillis();
        repository.addOrUpdateShipperLocation(LOCATION_SHIPPER, 10.77, 106.70, true, onlineTimestamp);
        assertThat(nearbyShipperIds()).contains(LOCATION_SHIPPER);

        long offlineTimestamp = onlineTimestamp + 100;
        repository.markShipperOffline(LOCATION_SHIPPER, offlineTimestamp);
        assertThat(nearbyShipperIds()).doesNotContain(LOCATION_SHIPPER);

        repository.addOrUpdateShipperLocation(
                LOCATION_SHIPPER, 10.77, 106.70, true, onlineTimestamp + 50);

        assertThat(nearbyShipperIds()).doesNotContain(LOCATION_SHIPPER);
    }

    @Test
    void newerOnlineUpdateAfterOfflineTombstoneRestoresMatchingEligibility() {
        long timestamp = System.currentTimeMillis();
        repository.markShipperOffline(LOCATION_SHIPPER, timestamp);

        repository.addOrUpdateShipperLocation(
                LOCATION_SHIPPER, 10.77, 106.70, true, timestamp + 100);

        assertThat(nearbyShipperIds()).contains(LOCATION_SHIPPER);
    }

    private java.util.List<Long> nearbyShipperIds() {
        return repository.findNearbyShippers(10.77, 106.70, 5.0, 10)
                .stream()
                .map(result -> result.shipperId)
                .toList();
    }
}
