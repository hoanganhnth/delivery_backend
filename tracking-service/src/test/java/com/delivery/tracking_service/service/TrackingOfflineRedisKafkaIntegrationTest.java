package com.delivery.tracking_service.service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.entity.ShipperIdentityProjection;
import com.delivery.tracking_service.repository.RedisGeoRepository;
import com.delivery.tracking_service.repository.ShipperIdentityProjectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Production HTTP/core/Redis/JSON producer proof; realtime offline does not require a PostgreSQL write. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tracking_offline_wire;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "app.shipper.identity-projection.enforced=true", "app.auth.jwks-uri=http://localhost:8081/.well-known/jwks.json",
        "app.internal.secret=offline-proof-only", "delivery.service.url=http://delivery-service"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TrackingOfflineRedisKafkaIntegrationTest {
    private static final String TOPIC = "shipper.location-updated";
    @Container static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void boundary(DynamicPropertyRegistry properties) {
        properties.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
    @Autowired MockMvc mvc;
    @Autowired RedisGeoRepository locations;
    @Autowired RedisTemplate<String, Object> redis;
    @Autowired ShipperIdentityProjectionRepository projections;
    @Autowired ObjectMapper mapper;

    @Test void internalAndAuthenticatedOfflineClearRedisAndEmitExactTombstonesWithOrWithoutCoordinates() throws Exception {
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get(20, TimeUnit.SECONDS);
        }
        try (var consumer = new KafkaConsumer<String, String>(Map.of("bootstrap.servers", KAFKA.getBootstrapServers(),
                "group.id", "offline-proof-" + UUID.randomUUID(), "enable.auto.commit", false,
                "key.deserializer", StringDeserializer.class, "value.deserializer", StringDeserializer.class))) {
            consumer.assign(List.of(new TopicPartition(TOPIC, 0))); consumer.seekToBeginning(consumer.assignment());
            long before = System.currentTimeMillis();
            seedMembership(7001L);
            mvc.perform(post("/api/tracking/internal/shippers/7001/offline")).andExpect(status().isForbidden());
            mvc.perform(post("/api/tracking/internal/shippers/7001/offline").header("Internal-Token", "wrong"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/tracking/internal/shippers/0/offline").header("Internal-Token", "offline-proof-only"))
                    .andExpect(status().isBadRequest());
            assertThat(redis.opsForSet().isMember("shippers:online:set", "7001")).isTrue();
            mvc.perform(post("/api/tracking/internal/shippers/7001/offline").header("Internal-Token", "offline-proof-only"))
                    .andExpect(status().isOk());
            assertOfflineMembership(7001L);
            assertThat(locations.getCachedShipperLocation(7001L)).isNull();

            var mapping = new ShipperIdentityProjection(); mapping.setPrincipalId(100L); mapping.setLegacyUserId(200L);
            mapping.setShipperId(7002L); mapping.setMappingVersion(1L); mapping.setUpdatedAt(LocalDateTime.now());
            projections.saveAndFlush(mapping);
            var current = new ShipperLocationResponse(); current.setShipperId(7002L); current.setLatitude(10.77);
            current.setLongitude(106.7); current.setAccuracy(3.5); current.setSpeed(12.0); current.setHeading(90.0);
            current.setDistance(1.2); current.setIsOnline(true);
            locations.cacheShipperLocation(7002L, current);
            mvc.perform(post("/api/tracking/shipper-locations/offline").with(authentication(actor())))
                    .andExpect(status().isOk());
            assertOfflineMembership(7002L);
            var offline = locations.getCachedShipperLocation(7002L);
            assertThat(offline.getIsOnline()).isFalse(); assertThat(offline.getLatitude()).isEqualTo(10.77);
            assertThat(offline.getLongitude()).isEqualTo(106.7); assertThat(offline.getDistance()).isEqualTo(1.2);
            assertThat(offline.getLastPing()).isEqualTo(offline.getUpdatedAt());
            assertThat(LocalDateTime.parse(offline.getUpdatedAt())).isNotNull();

            var partial = new ShipperLocationResponse(); partial.setShipperId(7003L); partial.setLatitude(10.78); partial.setIsOnline(true);
            locations.cacheShipperLocation(7003L, partial); seedMembership(7003L);
            mvc.perform(post("/api/tracking/internal/shippers/7003/offline").header("Internal-Token", "offline-proof-only"))
                    .andExpect(status().isOk());
            assertOfflineMembership(7003L);
            assertThat(locations.getCachedShipperLocation(7003L).getLatitude()).isEqualTo(10.78);
            assertThat(locations.getCachedShipperLocation(7003L).getLongitude()).isNull();

            var missingMapping = new ShipperIdentityProjection(); missingMapping.setPrincipalId(101L); missingMapping.setLegacyUserId(201L);
            missingMapping.setShipperId(7004L); missingMapping.setMappingVersion(1L); missingMapping.setUpdatedAt(LocalDateTime.now());
            projections.saveAndFlush(missingMapping); seedMembership(7004L);
            mvc.perform(post("/api/tracking/shipper-locations/offline").with(authentication(actor(101L, 201L))))
                    .andExpect(status().isOk());
            assertOfflineMembership(7004L); assertThat(locations.getCachedShipperLocation(7004L)).isNull();

            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (records.size() < 4 && System.nanoTime() < deadline) consumer.poll(Duration.ofMillis(300)).forEach(records::add);
            assertThat(records).hasSize(4);
            Set<String> eventIds = new HashSet<>();
            Map<Long, JsonNode> events = new HashMap<>();
            for (var record : records) {
                var event = mapper.readTree(record.value()); long shipper = event.get("shipperId").asLong(); events.put(shipper, event);
                assertThat(record.key()).isEqualTo(Long.toString(shipper));
                assertThat(event.get("isOnline").asBoolean()).isFalse();
                assertThat(event.get("source").asText()).isEqualTo("OFFLINE_TOMBSTONE");
                assertThat(event.get("timestamp").asLong()).isBetween(before, System.currentTimeMillis());
                assertThat(UUID.fromString(event.get("eventId").asText())).isNotNull();
                eventIds.add(event.get("eventId").asText());
            }
            assertThat(eventIds).hasSize(4); assertThat(events.keySet()).containsExactlyInAnyOrder(7001L, 7002L, 7003L, 7004L);
            assertThat(events.get(7001L).get("latitude").isNull()).isTrue();
            assertThat(events.get(7001L).get("longitude").isNull()).isTrue();
            assertThat(events.get(7002L).get("accuracy").asDouble()).isEqualTo(3.5);
            assertThat(events.get(7002L).get("speed").asDouble()).isEqualTo(12.0);
            assertThat(events.get(7002L).get("heading").asDouble()).isEqualTo(90.0);
            assertThat(events.get(7003L).get("latitude").asDouble()).isEqualTo(10.78);
            assertThat(events.get(7003L).get("longitude").isNull()).isTrue();
            assertThat(events.get(7004L).get("latitude").isNull()).isTrue();
            assertThat(events.get(7004L).get("longitude").isNull()).isTrue();
        }
    }
    private void seedMembership(long shipper) {
        redis.opsForGeo().add("shippers:geo:locations", new Point(106.7, 10.77), Long.toString(shipper));
        redis.opsForSet().add("shippers:online:set", Long.toString(shipper));
    }
    private void assertOfflineMembership(long shipper) {
        assertThat(redis.opsForSet().isMember("shippers:online:set", Long.toString(shipper))).isFalse();
        assertThat(redis.opsForGeo().position("shippers:geo:locations", Long.toString(shipper)).get(0)).isNull();
    }
    private AuthenticatedActorAuthenticationToken actor() { return actor(100L, 200L); }
    private AuthenticatedActorAuthenticationToken actor(long principal, long legacy) {
        var jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject(Long.toString(legacy)).build();
        return new AuthenticatedActorAuthenticationToken(jwt, new AuthenticatedActor(principal, legacy, "fixture@example.test", Set.of("SHIPPER")),
                List.of(new SimpleGrantedAuthority("ROLE_SHIPPER")));
    }
}
