package com.delivery.match_service.listener;

import com.delivery.match_service.common.constants.KafkaTopicConstants;
import com.delivery.match_service.dto.event.FindShipperEvent;
import com.delivery.match_service.dto.event.ShipperNotFoundEvent;
import com.delivery.match_service.dto.event.ShipperFoundEvent;
import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import com.delivery.match_service.service.MatchCancellationService;
import com.delivery.match_service.service.MatchService;
import com.delivery.match_service.service.MatchEventPublisher;
import com.delivery.match_service.service.SettlementEligibilityClient;
import com.delivery.match_service.metrics.BusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;

/**
 * ✅ Kafka Event Listener cho Match Service theo Backend Instructions
 * Lắng nghe FindShipperEvent từ Delivery Service và chỉ publish
 * ShipperFoundEvent
 * Simplified: Chỉ dùng 1 event duy nhất cho dễ quản lý
 * ✅ Retry mechanism: Tìm shipper liên tục nếu chưa tìm thấy
 */
@Slf4j
@Component
public class FindShipperEventListener {

        private final MatchService matchService;
        private final MatchEventPublisher matchEventPublisher;
        private final MatchCancellationService matchCancellationService;
        private final SettlementEligibilityClient settlementEligibilityClient;
        private final int candidatePoolSize;
        private final ObjectMapper objectMapper;
        private final BusinessMetrics businessMetrics;

        // ✅ Default retry configuration (nếu Saga không gửi)
        private static final int DEFAULT_MAX_RETRY_ATTEMPTS = 10;
        private static final int DEFAULT_INITIAL_DELAY_SECONDS = 30;
        private static final int DEFAULT_MAX_DELAY_SECONDS = 300;
        private static final double DEFAULT_BACKOFF_MULTIPLIER = 1.5;

        // ✅ Constructor Injection Pattern (MANDATORY)
        @Autowired
        public FindShipperEventListener(
                        MatchService matchService,
                        MatchEventPublisher matchEventPublisher,
                        MatchCancellationService matchCancellationService,
                        SettlementEligibilityClient settlementEligibilityClient,
                        BusinessMetrics businessMetrics,
                        @Value("${matching.candidate-pool-size:20}") int candidatePoolSize) {
                this.matchService = matchService;
                this.matchEventPublisher = matchEventPublisher;
                this.matchCancellationService = matchCancellationService;
                this.settlementEligibilityClient = settlementEligibilityClient;
                this.businessMetrics = businessMetrics;
                this.candidatePoolSize = candidatePoolSize;
                this.objectMapper = new ObjectMapper()
                                .registerModule(new JavaTimeModule())
                                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        /** Compatibility constructor for focused listener tests; application wiring uses MeterRegistry. */
        FindShipperEventListener(MatchService matchService, MatchEventPublisher matchEventPublisher,
                        MatchCancellationService matchCancellationService,
                        SettlementEligibilityClient settlementEligibilityClient, int candidatePoolSize) {
                this(matchService, matchEventPublisher, matchCancellationService, settlementEligibilityClient,
                                new BusinessMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                                candidatePoolSize);
        }

        /**
         * ✅ Nhận lệnh từ Saga Orchestrator: Tìm shipper
         * Canonical topic: saga.command.find-shipper
         */
        @RetryableTopic(
                        attempts = "4",
                        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
                        retryTopicSuffix = ".retry",
                        dltTopicSuffix = ".DLT",
                        autoCreateTopics = "false")
        @KafkaListener(
                        topics = "saga.command.find-shipper",
                        containerFactory = "reactiveKafkaListenerContainerFactory")
        public Mono<Void> handleFindShipperEvent(
                        String message,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
                        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp) {

                FindShipperEvent event = null;
                try {
                        event = objectMapper.readValue(message, FindShipperEvent.class);
                        log.info("📥 Received FindShipperEvent for delivery: {} from topic: {} partition: {} timestamp: {}",
                                        event.getDeliveryId(), topic, partition, timestamp);

                        // ✅ Validate event data
                        if (event.getEventId() == null || event.getDeliveryId() == null
                                        || event.getDeliveryId() <= 0) {
                                throw new IllegalArgumentException(
                                                "Invalid FindShipperEvent: stable eventId and positive deliveryId are required");
                        }
                        if (event.getOrderId() == null || event.getOrderId() <= 0
                                        || event.getTotalPrice() == null
                                        || event.getTotalPrice().signum() <= 0
                                        || !"COD".equalsIgnoreCase(event.getPaymentMethod())) {
                                throw new IllegalArgumentException(
                                                "Invalid COD match contract: orderId, positive totalPrice and paymentMethod=COD are required");
                        }
                        if (!validVietnamCoordinate(event.getPickupLat(), event.getPickupLng())) {
                                throw new IllegalArgumentException(
                                                "Invalid match contract: canonical Vietnam pickup coordinates are required");
                        }
                        if (!hasText(event.getRestaurantName()) || !hasText(event.getPickupAddress())
                                        || !hasText(event.getDeliveryAddress())) {
                                throw new IllegalArgumentException(
                                                "Invalid match contract: canonical restaurant and address text are required");
                        }

                        // ✅ Start continuous shipper search with retry mechanism
                        return startContinuousShipperSearch(event);

                } catch (Exception e) {
                        Long deliveryId = (event != null) ? event.getDeliveryId() : null;
                        log.error("🔥 Unexpected error processing FindShipperEvent for delivery: {} - Error: {}",
                                        deliveryId, e.getMessage(), e);

                        throw new IllegalStateException("Failed to process find-shipper command", e);
                }
        }

        /**
         * ✅ Nhận lệnh từ Saga Orchestrator: Dừng tìm shipper (khi Saga timeout hoặc Order bị huỷ)
         */
        @KafkaListener(topics = "saga.command.stop-matching")
        public void handleStopMatchingCommand(
                        String message,
                        Acknowledgment acknowledgment) {
                try {
                        // Payload thường chỉ là orderId (String) hoặc JSON chứa orderId/deliveryId
                        // Ở đây SagaManager gửi rawEvent gốc hoặc orderId.toString()
                        // Ta sẽ parse để lấy deliveryId nếu có, hoặc ít nhất là đánh dấu cancel theo orderId nếu cần
                        // Tuy nhiên matchCancellationService đang dùng deliveryId
                        
                        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(message);
                        if (!node.hasNonNull("eventId")) {
                                throw new IllegalArgumentException("stop-matching eventId is required");
                        }
                        java.util.UUID.fromString(node.get("eventId").asText());
                        Long deliveryId = node.has("deliveryId") ? node.get("deliveryId").asLong() : null;
                        Long orderId = node.has("orderId") ? node.get("orderId").asLong() : null;
                        if (orderId == null || orderId <= 0) {
                                throw new IllegalArgumentException("stop-matching orderId must be positive");
                        }
                        
                        if (deliveryId != null && deliveryId > 0) {
                                log.warn("🛑 Received STOP_MATCHING command from Saga for delivery: {}", deliveryId);
                                matchCancellationService.markCancelled(deliveryId);
                        } else {
                                log.info("STOP_MATCHING for order {} has no delivery yet; nothing to cancel", orderId);
                        }
                        
                } catch (Exception e) {
                        log.error("💥 Error processing STOP_MATCHING command: {}", e.getMessage());
                        throw new IllegalStateException("Failed to process stop-matching command", e);
                }
                acknowledgment.acknowledge();
        }

        /**
         * ✅ Tìm shipper liên tục với exponential backoff retry
         */
        private Mono<Void> startContinuousShipperSearch(FindShipperEvent event) {
                AtomicInteger attemptCount = new AtomicInteger(0);

                // A cancellation tombstone is monotonic for a delivery ID. Never
                // clear it here: a delayed/retried find command must not resurrect
                // matching after order cancellation.
                if (matchCancellationService.isCancelled(event.getDeliveryId())) {
                        log.info("Matching command {} ignored because delivery {} is cancelled",
                                        event.getEventId(), event.getDeliveryId());
                        return Mono.empty();
                }

                // ✅ Convert event to request một lần
                FindNearbyShippersRequest request = createFindShippersRequest(event);
                Long systemUserId = 1L;
                String systemRole = "SYSTEM";

                // ✅ Lấy config từ Event (do Saga truyền xuống) hoặc dùng mặc định
                final int maxRetries = event.getMaxRetryAttempts() != null ? event.getMaxRetryAttempts() : DEFAULT_MAX_RETRY_ATTEMPTS;
                final int initialDelay = event.getInitialDelaySeconds() != null ? event.getInitialDelaySeconds() : DEFAULT_INITIAL_DELAY_SECONDS;
                final int maxDelay = event.getMaxDelaySeconds() != null ? event.getMaxDelaySeconds() : DEFAULT_MAX_DELAY_SECONDS;
                final double backoffMulti = event.getBackoffMultiplier() != null ? event.getBackoffMultiplier() : DEFAULT_BACKOFF_MULTIPLIER;

                // ✅ Reactive retry với exponential backoff
                return matchService.findNearbyShippers(request, systemUserId, systemRole)
                                // ✅ Cancel fast: if delivery already cancelled, stop chain immediately
                                .flatMap(shippers -> {
                                        if (matchCancellationService.isCancelled(event.getDeliveryId())) {
                                                return Mono.error(new MatchingCancelledException());
                                        }
                                        return Mono.just(shippers);
                                })
                .flatMap(shippers -> {
                        if (shippers != null && !shippers.isEmpty()) {
                                // ✅ Filter out excluded shippers (previously rejected this order)
                                java.util.List<Long> excluded = event.getExcludedShipperIds();
                                if (excluded != null && !excluded.isEmpty()) {
                                        List<NearbyShipperResponse> filtered = shippers.stream()
                                                .filter(s -> !excluded.contains(s.getShipperId()))
                                                .collect(java.util.stream.Collectors.toList());
                                        
                                        log.info("🔍 Filtered shippers: {} total, {} excluded, {} remaining for delivery: {}",
                                                shippers.size(), excluded.size(), filtered.size(), event.getDeliveryId());
                                        
                                        if (filtered.isEmpty()) {
                                                        return Mono.error(
                                                        new NoShipperAvailableException("No shippers found for delivery: "
                                                                + event.getDeliveryId() + " (all filtered by exclusion list)"));
                                        }
                                        return Mono.just(filtered);
                                }
                                // ✅ Tìm thấy shipper, trả về kết quả
                                return Mono.just(shippers);
                        } else {
                                // ✅ Không tìm thấy shipper, trigger retry
                                return Mono.error(
                                                new NoShipperAvailableException("No shippers found for delivery: "
                                                                + event.getDeliveryId()));
                        }
                })
                                .flatMap(shippers -> selectEligibleShipper(event, shippers))
                                .flatMap(shippers -> {
                                        NearbyShipperResponse selected = shippers.get(0);
                                        if (matchService.tryReserveShipperOffer(
                                                        selected.getShipperId(), event.getDeliveryId(), 180)) {
                                                return Mono.just(List.of(selected));
                                        }
                                        return Mono.error(new NoShipperAvailableException(
                                                        "No shippers found for delivery: " + event.getDeliveryId()
                                                                        + " (reservation race)"));
                                })
                                .retryWhen(Retry.backoff(maxRetries, Duration.ofSeconds(initialDelay))
                                                .maxBackoff(Duration.ofSeconds(maxDelay))
                                                .multiplier(backoffMulti)
                                                .doBeforeRetry(retrySignal -> {
                                                        // ✅ Nếu đã cancel thì đừng schedule retry nữa
                                                        if (matchCancellationService
                                                                        .isCancelled(event.getDeliveryId())) {
                                                                throw new MatchingCancelledException();
                                                        }

                                                        int attempt = attemptCount.incrementAndGet();
                                                        long delayMs = retrySignal.totalRetries() == 0
                                                                        ? initialDelay * 1000L
                                                                        : Math.min(
                                                                                        (long) (initialDelay
                                                                                                        * Math.pow(backoffMulti,
                                                                                                                        retrySignal.totalRetries())
                                                                                                        * 1000),
                                                                                        maxDelay * 1000L);

                                                        log.info("🔄 Retry attempt {}/{} for delivery: {} - Next retry in {}ms",
                                                                        attempt, maxRetries,
                                                                        event.getDeliveryId(), delayMs);
                                                })
                                                .filter(throwable -> {
                                                        // ✅ Chỉ retry nếu không tìm thấy shipper (empty result)
                                                        // Không retry nếu có lỗi system khác
                                                        return throwable instanceof NoShipperAvailableException;
                                                }))
                                .flatMap(shippers -> {
                                        if (matchCancellationService.isCancelled(event.getDeliveryId())) {
                                                NearbyShipperResponse selected = shippers.get(0);
                                                matchService.releaseShipperOffer(
                                                                selected.getShipperId(), event.getDeliveryId());
                                                log.info("🛑 Delivery {} cancelled while matching; skip publish found event",
                                                                event.getDeliveryId());
                                                return Mono.<Void>empty();
                                        }

                                        log.info("✅ Selected nearest shipper from {} candidates for delivery: {} after {} attempts",
                                                        shippers.size(), event.getDeliveryId(), attemptCount.get() + 1);
                                        matchEventPublisher.publishShipperFoundEvent(
                                                        createShipperFoundEvent(event, shippers));
                                        log.info("✅ Published single-shipper offer candidate for delivery: {}",
                                                        event.getDeliveryId());
                                        return Mono.<Void>empty();
                                })
                                .onErrorResume(error -> {
                                        if (hasCause(error, MatchingCancelledException.class)) {
                                                log.info("🛑 Matching stopped because delivery {} was cancelled",
                                                                event.getDeliveryId());
                                                return Mono.<Void>empty();
                                        }

                                        log.error("💥 Failed to find shippers for delivery: {} after {} attempts - Error: {}",
                                                        event.getDeliveryId(), maxRetries, error.getMessage());
                                        if (!hasCause(error, NoShipperAvailableException.class)) {
                                                // Propagate infrastructure failures to @RetryableTopic. The
                                                // adapter moves exhausted records to saga.command.find-shipper.DLT.
                                                return Mono.<Void>error(error);
                                        }

                                        ShipperNotFoundEvent notFoundEvent = new ShipperNotFoundEvent(
                                                        event.getDeliveryId(), event.getOrderId(), maxRetries);
                                        notFoundEvent.setEventId(outcomeEventId(
                                                        "shipper-not-found", event.getEventId()).toString());
                                        notFoundEvent.setSearchRadius(request.getRadiusKm());
                                        notFoundEvent.setPickupLat(request.getLatitude());
                                        notFoundEvent.setPickupLng(request.getLongitude());
                                        matchEventPublisher.publishShipperNotFoundEvent(notFoundEvent);
                                        businessMetrics.record("shipper_not_found");
                                        log.info("✅ Published ShipperNotFoundEvent for delivery: {} after {} failed attempts",
                                                        event.getDeliveryId(), maxRetries);
                                        return Mono.<Void>empty();
                                });
        }

        private boolean hasCause(Throwable error, Class<? extends Throwable> causeType) {
                Throwable current = error;
                while (current != null) {
                        if (causeType.isInstance(current)) {
                                return true;
                        }
                        current = current.getCause();
                }
                return false;
        }

        private static final class NoShipperAvailableException extends RuntimeException {
                private NoShipperAvailableException(String message) {
                        super(message);
                }
        }

        private static final class MatchingCancelledException extends RuntimeException {
                private MatchingCancelledException() {
                        super("DELIVERY_CANCELLED");
                }
        }

        /**
         * ✅ Convert FindShipperEvent to FindNearbyShippersRequest với null safety
         */
        private FindNearbyShippersRequest createFindShippersRequest(FindShipperEvent event) {
                FindNearbyShippersRequest request = new FindNearbyShippersRequest();

                // Pickup is server-owned canonical restaurant data. Missing or
                // invalid coordinates are rejected before this method; never
                // match around the delivery address or a synthetic city center.
                request.setLatitude(event.getPickupLat());
                request.setLongitude(event.getPickupLng());

                log.debug("🎯 Using canonical pickup location: {}, {} for delivery: {}",
                                event.getPickupLat(), event.getPickupLng(), event.getDeliveryId());

                // Default search parameters
                request.setRadiusKm(5.0); // 5km radius
                // Inspect a bounded nearest-candidate pool for COD eligibility, then
                // reserve and publish only one offer.
                request.setMaxShippers(candidatePoolSize);

                return request;
        }

        private boolean validVietnamCoordinate(Double latitude, Double longitude) {
                return latitude != null && longitude != null
                                && Double.isFinite(latitude) && Double.isFinite(longitude)
                                && latitude >= 8.0 && latitude <= 24.0
                                && longitude >= 102.0 && longitude <= 110.0;
        }

        private boolean hasText(String value) {
                return value != null && !value.isBlank();
        }

        /**
         * ✅ Convert tìm được shippers thành ShipperFoundEvent với đầy đủ thông tin
         */
        private ShipperFoundEvent createShipperFoundEvent(FindShipperEvent event,
                        List<NearbyShipperResponse> shippers) {
                List<ShipperFoundEvent.ShipperMatchResult> matchResults = shippers.stream()
                                .limit(1)
                                .map(shipper -> new ShipperFoundEvent.ShipperMatchResult(
                                                shipper.getShipperId(),
                                                shipper.getShipperName(),
                                                shipper.getShipperPhone(),
                                                shipper.getDistanceKm(),
                                                shipper.getLatitude(),
                                                shipper.getLongitude(),
                                                null,
                                                shipper.isOnline()))
                                .collect(java.util.stream.Collectors.toList());

                // ✅ Tạo ShipperFoundEvent với đầy đủ thông tin cho cả delivery-service và
                // notification-service
                ShipperFoundEvent foundEvent = new ShipperFoundEvent(event.getDeliveryId(), event.getOrderId(),
                                matchResults);
                foundEvent.setEventId(outcomeEventId("shipper-found", event.getEventId()).toString());
                foundEvent.setMatchingSessionId(event.getEventId().toString());

                // ✅ Set additional info từ FindShipperEvent
                foundEvent.setRestaurantName(event.getRestaurantName());
                foundEvent.setPickupAddress(event.getPickupAddress());
                foundEvent.setDeliveryAddress(event.getDeliveryAddress());
                foundEvent.setPickupLat(event.getPickupLat());
                foundEvent.setPickupLng(event.getPickupLng());
                foundEvent.setDeliveryLat(event.getDeliveryLat());
                foundEvent.setDeliveryLng(event.getDeliveryLng());
                foundEvent.setTotalPrice(event.getTotalPrice());
                foundEvent.setPaymentMethod(event.getPaymentMethod());

                return foundEvent;
        }

        private java.util.UUID outcomeEventId(String outcome, java.util.UUID commandEventId) {
                String identity = "match:" + outcome + ":" + commandEventId;
                return java.util.UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        }

        private Mono<List<NearbyShipperResponse>> selectEligibleShipper(
                        FindShipperEvent event,
                        List<NearbyShipperResponse> shippers) {
                return Flux.fromIterable(shippers)
                                .concatMap(shipper -> settlementEligibilityClient
                                                .isCodEligible(shipper.getShipperId(), event.getTotalPrice())
                                                .filter(Boolean::booleanValue)
                                                .map(ignored -> shipper))
                                .next()
                                .map(List::of)
                                .switchIfEmpty(Mono.error(new NoShipperAvailableException(
                                                "No COD-eligible shipper found for delivery: " + event.getDeliveryId())));
        }
}
