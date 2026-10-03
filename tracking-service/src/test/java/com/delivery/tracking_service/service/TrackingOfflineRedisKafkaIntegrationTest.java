package com.delivery.tracking_service.service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.entity.ShipperIdentityProjection;
import com.delivery.tracking_service.repository.RedisGeoRepository;
import com.delivery.tracking_service.repository.ShipperIdentityProjectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.delivery.tracking.application.api.TrackingPort;
import com.delivery.identity.contracts.SimulationContext;
import com.delivery.tracking_service.repository.ShipperDeliveryAssignmentStore;
import com.delivery.tracking_service.websocket.*;
import com.delivery.tracking_service.listener.RedisLocationFanoutListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.awaitility.Awaitility.await;
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

/** Real Redis/Kafka and independent subscriber-handler proof for HTTP, WebSocket and offline publication. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tracking_offline_wire;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "app.shipper.identity-projection.enforced=true", "app.auth.jwks-uri=http://localhost:8081/.well-known/jwks.json",
        "app.internal.secret=offline-proof-only", "delivery.service.url=http://delivery-service"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
    @Autowired TrackingPort core;
    @Autowired ShipperLocationWebSocketHandler publisherHandler;
    @Autowired ShipperPublisherSessionManager publishers;
    @Autowired ShipperIdentityResolver identities;
    @Autowired LocationFanoutPublisher fanout;
    @Autowired ShipperDeliveryAssignmentStore assignments;
    @Autowired RedisConnectionFactory connections;

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

            // Explicitly malformed flags must be rejected before any cache or broker mutation.
            String cachedTime = locations.getCachedShipperLocation(7002L).getUpdatedAt();
            for (String flag : List.of("null", "1", "0", "\"true\"", "\"false\"", "[]", "{}")) {
                mvc.perform(post("/api/tracking/shipper-locations/update").with(authentication(actor()))
                        .contentType("application/json").content("{\"latitude\":10.8,\"longitude\":106.7,\"isOnline\":" + flag + "}"))
                        .andExpect(status().isBadRequest());
                assertThat(locations.getCachedShipperLocation(7002L).getUpdatedAt()).isEqualTo(cachedTime);
            }
            publishThroughIndependentSubscriber();

            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (records.size() < 8 && System.nanoTime() < deadline) consumer.poll(Duration.ofMillis(300)).forEach(records::add);
            assertThat(records).hasSize(8);
            Set<String> eventIds = new HashSet<>();
            Map<Long, JsonNode> events = new HashMap<>();
            for (var record : records) {
                var event = mapper.readTree(record.value()); long shipper = event.get("shipperId").asLong();
                if (event.get("source").asText().equals("OFFLINE_TOMBSTONE")) {
                    events.putIfAbsent(shipper, event); assertThat(event.get("isOnline").asBoolean()).isFalse();
                } else {
                    assertThat(shipper).isEqualTo(7002); assertThat(event.get("isOnline").asBoolean()).isTrue();
                    assertThat(event.get("source").asText()).isIn("WEBSOCKET", "APPLICATION");
                    assertThat(event.get("deliveryId").asLong()).isEqualTo(9002);
                }
                assertThat(record.key()).isEqualTo(Long.toString(shipper));
                assertThat(event.get("simulationContext")).isEqualTo(mapper.valueToTree(SimulationContext.real()));
                assertThat(event.get("timestamp").asLong()).isBetween(before, System.currentTimeMillis());
                assertThat(UUID.fromString(event.get("eventId").asText())).isNotNull();
                eventIds.add(event.get("eventId").asText());
            }
            var sources = records.stream().map(record -> {
                try { return mapper.readTree(record.value()).get("source").asText(); }
                catch (Exception failure) { throw new AssertionError(failure); }
            }).toList();
            assertThat(sources).containsExactly("OFFLINE_TOMBSTONE", "OFFLINE_TOMBSTONE", "OFFLINE_TOMBSTONE", "OFFLINE_TOMBSTONE", "WEBSOCKET", "APPLICATION", "OFFLINE_TOMBSTONE", "OFFLINE_TOMBSTONE");
            assertThat(eventIds).hasSize(8); assertThat(events.keySet()).containsExactlyInAnyOrder(7001L, 7002L, 7003L, 7004L);
            assertThat(events.get(7001L).get("latitude").isNull()).isTrue();
            assertThat(events.get(7001L).get("longitude").isNull()).isTrue();
            assertThat(events.get(7002L).get("accuracy").asDouble()).isEqualTo(3.5);
            assertThat(events.get(7002L).get("speed").asDouble()).isEqualTo(12.0);
            assertThat(events.get(7002L).get("heading").asDouble()).isEqualTo(90.0);
            assertThat(events.get(7003L).get("latitude").asDouble()).isEqualTo(10.78);
            assertThat(events.get(7003L).get("longitude").isNull()).isTrue();
            assertThat(events.get(7004L).get("latitude").isNull()).isTrue();
            assertThat(events.get(7004L).get("longitude").isNull()).isTrue();
            var cachedOffline = mapper.readTree(records.get(6).value());
            assertThat(cachedOffline.get("shipperId").asLong()).isEqualTo(7002L);
            assertThat(cachedOffline.get("latitude").asDouble()).isEqualTo(10.8);
            assertThat(cachedOffline.get("longitude").asDouble()).isEqualTo(106.7);
            assertThat(cachedOffline.get("deliveryId").asLong()).isEqualTo(9002L);
            var identityOffline = mapper.readTree(records.get(7).value());
            assertThat(identityOffline.get("shipperId").asLong()).isEqualTo(7002L);
            assertThat(identityOffline.get("latitude").isNull()).isTrue();
            assertThat(identityOffline.get("longitude").isNull()).isTrue();
            assertThat(identityOffline.get("deliveryId").asLong()).isEqualTo(9002L);

        }
    }
    private void publishThroughIndependentSubscriber() throws Exception {
        assignments.busy(7002L, 9002L, System.currentTimeMillis(), UUID.randomUUID().toString());
        var access = mock(DeliveryTrackingAccessClient.class);
        when(access.canTrack(9002L, 300L, "USER", 7002L)).thenReturn(true);
        var dispatcher = new LocationMessageDispatcher(1, 64);
        var rooms = new DeliveryRoomRegistry();
        var subscriptions = new com.delivery.tracking.application.DefaultDeliveryRoomSubscriptionUseCase(assignments, rooms);
        var subscriberHandler = new ShipperLocationWebSocketHandler(mapper, locations, core, access, publishers,
                rooms, dispatcher, fanout, identities, subscriptions);
        var receiver = new RedisMessageListenerContainer(); receiver.setConnectionFactory(connections);
        receiver.addMessageListener(new RedisLocationFanoutListener(mapper, subscriberHandler), new ChannelTopic(LocationFanoutPublisher.CHANNEL));
        try {
            receiver.afterPropertiesSet(); receiver.start();
            var messages = new CopyOnWriteArrayList<String>(); var deniedMessages = new CopyOnWriteArrayList<String>();
            var subscriber = session("remote-subscriber", 1300L, 300L, "USER", messages);
            subscriberHandler.afterConnectionEstablished(subscriber);
            subscriberHandler.handleMessage(subscriber, new TextMessage("{\"action\":\"subscribe_shipper\",\"deliveryId\":9002,\"shipperId\":7002}"));
            var denied = session("denied-subscriber", 1301L, 301L, "USER", deniedMessages);
            subscriberHandler.afterConnectionEstablished(denied);
            subscriberHandler.handleMessage(denied, new TextMessage("{\"action\":\"subscribe_shipper\",\"deliveryId\":9002,\"shipperId\":7002}"));
            assertThat(deniedMessages).anyMatch(message -> message.contains("FORBIDDEN"));
            var publisher = session("real-publisher", 100L, 200L, "SHIPPER", new CopyOnWriteArrayList<>());
            publisherHandler.afterConnectionEstablished(publisher);
            publisherHandler.handleMessage(publisher, new TextMessage("{\"action\":\"update_location\",\"shipperId\":999,\"latitude\":10.79,\"longitude\":106.69}"));
            awaitLocation(messages, 10.79);
            var websocket = locations.getCachedShipperLocation(7002L);
            assertThat(websocket.getShipperId()).isEqualTo(7002L); assertThat(websocket.getAccuracy()).isNull();
            assertThat(websocket.getLastPing()).isEqualTo(websocket.getUpdatedAt());
            assertThat(LocalDateTime.parse(websocket.getUpdatedAt())).isNotNull();
            assertThat(redis.opsForSet().isMember("shippers:online:set", "7002")).isTrue();
            // Source/identity metadata is server-owned; omitted online flag retains the existing true default.
            String response = mvc.perform(post("/api/tracking/shipper-locations/update").with(authentication(actor()))
                    .contentType("application/json").content("{\"shipperId\":999,\"source\":\"WEBSOCKET\",\"latitude\":10.8,\"longitude\":106.7}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var data = mapper.readTree(response).get("data"); assertThat(data.get("shipperId").asLong()).isEqualTo(7002);
            assertThat(data.get("isOnline").asBoolean()).isTrue();
            assertThat(java.time.Instant.parse(data.get("updatedAt").asText())).isNotNull();
            awaitLocation(messages, 10.8);
            messages.clear();
            mvc.perform(post("/api/tracking/shipper-locations/offline").with(authentication(actor())))
                    .andExpect(status().isOk());
            awaitOffline(messages, false);
            assertOfflineMembership(7002L);
            messages.clear(); locations.removeShipperLocationCache(7002L);
            mvc.perform(post("/api/tracking/internal/shippers/7002/offline").header("Internal-Token", "offline-proof-only"))
                    .andExpect(status().isOk());
            awaitOffline(messages, true);
            assertThat(deniedMessages).noneMatch(message -> message.contains("\"type\":\"location_update\""));
        } finally {
            receiver.stop(); receiver.destroy(); ReflectionTestUtils.invokeMethod(dispatcher, "shutdown");
        }
    }
    @Test
    void independentRedisSubscriberKeepsBothAuthorizedBatchAudiences() throws Exception {
        long time=System.currentTimeMillis();
        assignments.busyBatch(8002,9102,time,UUID.randomUUID().toString());
        assignments.busyBatch(8002,9103,time,UUID.randomUUID().toString());
        var rooms=new DeliveryRoomRegistry(); var dispatcher=new LocationMessageDispatcher(1,64);
        var access=mock(DeliveryTrackingAccessClient.class);
        when(access.canTrack(9102L,300L,"USER",8002L)).thenReturn(true);
        when(access.canTrack(9103L,301L,"USER",8002L)).thenReturn(true);
        var subscriptions=new com.delivery.tracking.application.DefaultDeliveryRoomSubscriptionUseCase(assignments,rooms);
        var handler=new ShipperLocationWebSocketHandler(mapper,locations,core,access,publishers,rooms,dispatcher,fanout,identities,subscriptions);
        var receiver=new RedisMessageListenerContainer(); receiver.setConnectionFactory(connections);
        receiver.addMessageListener(new RedisLocationFanoutListener(mapper,handler),new ChannelTopic(LocationFanoutPublisher.CHANNEL));
        receiver.afterPropertiesSet(); receiver.start();
        var firstMessages=new CopyOnWriteArrayList<String>(); var secondMessages=new CopyOnWriteArrayList<String>();
        var deniedMessages=new CopyOnWriteArrayList<String>();
        try {
            var first=session("batch-first",1300L,300L,"USER",firstMessages);
            var second=session("batch-second",1301L,301L,"USER",secondMessages);
            var denied=session("batch-denied",1302L,302L,"USER",deniedMessages);
            handler.afterConnectionEstablished(first); handler.afterConnectionEstablished(second); handler.afterConnectionEstablished(denied);
            handler.handleMessage(first,new TextMessage("{\"action\":\"subscribe_shipper\",\"deliveryId\":9102,\"shipperId\":8002}"));
            handler.handleMessage(second,new TextMessage("{\"action\":\"subscribe_shipper\",\"deliveryId\":9103,\"shipperId\":8002}"));
            handler.handleMessage(denied,new TextMessage("{\"action\":\"subscribe_shipper\",\"deliveryId\":9102,\"shipperId\":8002}"));
            var location=new ShipperLocationResponse(); location.setShipperId(8002L); location.setLatitude(10.8); location.setLongitude(106.7);
            location.setIsOnline(true); location.setUpdatedAt(java.time.Instant.now().toString()); location.setLastPing(location.getUpdatedAt());
            locations.cacheShipperLocation(8002L,location); fanout.publish(location);
            awaitLocation(firstMessages,10.8); awaitLocation(secondMessages,10.8);
            assertThat(deniedMessages).anyMatch(message->message.contains("FORBIDDEN"));
            assertThat(deniedMessages).noneMatch(message->message.contains("location_update"));
            var assignmentCore=new com.delivery.tracking.application.DefaultDeliveryRoomAssignmentUseCase(assignments,rooms);
            assignmentCore.apply(new com.delivery.tracking.application.api.DeliveryRoomAssignmentCommand(8002,9102,7,time+1,
                    UUID.randomUUID().toString(),"AVAILABLE",true));
            location.setLatitude(10.81); locations.cacheShipperLocation(8002L,location); fanout.publish(location);
            awaitLocation(secondMessages,10.81);
            assertThat(firstMessages).noneMatch(message->message.contains("10.81"));
            assertThat(rooms.activeDeliveries(8002)).containsExactly(9103L);
        } finally {receiver.stop();receiver.destroy();ReflectionTestUtils.invokeMethod(dispatcher,"shutdown");}
    }

    private void awaitOffline(List<String> messages, boolean withoutCoordinates) {
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            var updates = messages.stream().map(message -> {
                try { return mapper.readTree(message); }
                catch (Exception failure) { throw new AssertionError(failure); }
            }).filter(message -> "location_update".equals(message.path("type").asText())).toList();
            assertThat(updates).isNotEmpty();
            var update = updates.get(updates.size() - 1);
            assertThat(update.toString()).contains("\"isOnline\":false");
            assertThat(update.toString()).contains("\"shipperId\":7002");
            assertThat(update.toString()).contains(withoutCoordinates ? "\"latitude\":null" : "\"latitude\":10.8");
        });
    }
    private void awaitLocation(List<String> messages, double latitude) {
        String expected = "\"latitude\":" + latitude;
        try {
            await().atMost(Duration.ofSeconds(8)).until(() -> messages.stream().anyMatch(message -> message.contains(expected)));
        } catch (org.awaitility.core.ConditionTimeoutException timeout) {
            throw new AssertionError("Independent subscriber did not receive " + expected + ": " + messages, timeout);
        }
    }
    private WebSocketSession session(String id, long principal, long legacy, String role, List<String> messages) throws Exception {
        var session = mock(WebSocketSession.class); when(session.getId()).thenReturn(id); when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new HashMap<>(Map.of("authenticatedPrincipalId", principal, "authenticatedUserId", legacy, "authenticatedRole", role)));
        doAnswer(call -> { messages.add(((TextMessage) call.getArgument(0)).getPayload()); return null; }).when(session).sendMessage(any());
        return session;
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
