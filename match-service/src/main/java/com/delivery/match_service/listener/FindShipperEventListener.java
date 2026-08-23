package com.delivery.match_service.listener;

import com.delivery.match_service.dto.event.FindShipperEvent;
import com.delivery.match_service.dto.event.MatchingDecisionTraceEvent;
import com.delivery.match_service.dto.event.ShipperNotFoundEvent;
import com.delivery.match_service.dto.event.ShipperFoundEvent;
import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import com.delivery.match_service.service.MatchCancellationService;
import com.delivery.match_service.service.MatchCancellationProjectionRelay;
import com.delivery.match_service.service.MatchCommandStore;
import com.delivery.match_service.service.MatchService;
import com.delivery.match_service.service.SettlementEligibilityClient;
import com.delivery.match_service.service.DispatchPoolService;
import com.delivery.match_service.config.MatchingBatchProperties;
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
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
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
        private final MatchCommandStore matchCommandStore;
        private final MatchCancellationService matchCancellationService;
        private final MatchCancellationProjectionRelay cancellationProjectionRelay;
        private final SettlementEligibilityClient settlementEligibilityClient;
        private final int candidatePoolSize;
	private final ObjectMapper objectMapper;
	private final BusinessMetrics businessMetrics;
        private final Clock clock;
        private final DispatchPoolService dispatchPoolService;
        private final MatchingBatchProperties matchingBatchProperties;

        // ✅ Default retry configuration (nếu Saga không gửi)
        private static final int DEFAULT_MAX_RETRY_ATTEMPTS = 10;
        private static final int DEFAULT_INITIAL_DELAY_SECONDS = 30;
        private static final int DEFAULT_MAX_DELAY_SECONDS = 300;
        private static final double DEFAULT_BACKOFF_MULTIPLIER = 1.5;

        // ✅ Constructor Injection Pattern (MANDATORY)
        @Autowired
	public FindShipperEventListener(
                        MatchService matchService,
                        MatchCommandStore matchCommandStore,
                        MatchCancellationService matchCancellationService,
                        MatchCancellationProjectionRelay cancellationProjectionRelay,
                        SettlementEligibilityClient settlementEligibilityClient,
				BusinessMetrics businessMetrics,
				DispatchPoolService dispatchPoolService,
				MatchingBatchProperties matchingBatchProperties,
				@Value("${matching.candidate-pool-size:20}") int candidatePoolSize) {
		this(matchService, matchCommandStore, matchCancellationService, cancellationProjectionRelay, settlementEligibilityClient,
				businessMetrics, dispatchPoolService, matchingBatchProperties, candidatePoolSize, Clock.systemDefaultZone());
	}

        FindShipperEventListener(
				MatchService matchService,
				MatchCommandStore matchCommandStore,
				MatchCancellationService matchCancellationService,
				MatchCancellationProjectionRelay cancellationProjectionRelay,
				SettlementEligibilityClient settlementEligibilityClient,
				BusinessMetrics businessMetrics,
				DispatchPoolService dispatchPoolService,
				MatchingBatchProperties matchingBatchProperties,
				int candidatePoolSize,
				Clock clock) {
		this.matchService = matchService;
		this.matchCommandStore = matchCommandStore;
		this.matchCancellationService = matchCancellationService;
		this.cancellationProjectionRelay = cancellationProjectionRelay;
		this.settlementEligibilityClient = settlementEligibilityClient;
		this.businessMetrics = businessMetrics;
		this.candidatePoolSize = candidatePoolSize;
		this.clock = clock;
		this.dispatchPoolService = dispatchPoolService;
		this.matchingBatchProperties = matchingBatchProperties;
				this.objectMapper = new ObjectMapper()
                                .registerModule(new JavaTimeModule())
                                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        FindShipperEventListener(
                                MatchService matchService,
                                MatchCommandStore matchCommandStore,
                                MatchCancellationService matchCancellationService,
                                MatchCancellationProjectionRelay cancellationProjectionRelay,
                                SettlementEligibilityClient settlementEligibilityClient,
                                BusinessMetrics businessMetrics,
                                int candidatePoolSize,
                                Clock clock) {
                this(matchService, matchCommandStore, matchCancellationService, cancellationProjectionRelay,
                                settlementEligibilityClient, businessMetrics, null, null, candidatePoolSize, clock);
        }

        /** Compatibility constructor for focused listener tests; application wiring uses MeterRegistry. */
	FindShipperEventListener(MatchService matchService, MatchCommandStore matchCommandStore,
				MatchCancellationService matchCancellationService,
				MatchCancellationProjectionRelay cancellationProjectionRelay,
				SettlementEligibilityClient settlementEligibilityClient, int candidatePoolSize) {
		this(matchService, matchCommandStore, matchCancellationService, cancellationProjectionRelay, settlementEligibilityClient,
						new BusinessMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()), null, null,
						candidatePoolSize, Clock.systemDefaultZone());
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
                        topics = "${app.kafka.topics.find-shipper:saga.command.find-shipper}",
                        containerFactory = "reactiveKafkaListenerContainerFactory",
                        autoStartup = "${match.kafka.find-listener.auto-startup:${match.kafka.listener.auto-startup:true}}")
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

                        MatchCommandStore.CommandDecision decision =
                                        matchCommandStore.acceptFindCommand(
                                                        "saga.command.find-shipper", message, event);
                        if (decision.mode() == MatchCommandStore.CommandMode.TERMINAL) {
                                log.info("Durable Match command {} is already terminal; skipping replay",
                                                event.getEventId());
                                return Mono.empty();
                        }
                        if (decision.mode() == MatchCommandStore.CommandMode.RESUME_CANDIDATE) {
                                return resumeStagedCandidate(event, decision.stagedCandidate());
                        }

                        if (batchDispatchEnabled(event)) {
                                final FindShipperEvent dispatchEvent = event;
                                return Mono.fromRunnable(() -> dispatchPoolService.enqueue(dispatchEvent, null))
                                                .subscribeOn(Schedulers.boundedElastic())
                                                .then();
                        }

                        // Start continuous shipper search only after the command inbox commits.
                        return startContinuousShipperSearch(event);

                } catch (Exception e) {
                        Long deliveryId = (event != null) ? event.getDeliveryId() : null;
                        log.error("🔥 Unexpected error processing FindShipperEvent for delivery: {} - Error: {}",
                                        deliveryId, e.getMessage(), e);

                        throw new IllegalStateException("Failed to process find-shipper command", e);
                }
        }

        private boolean batchDispatchEnabled(FindShipperEvent event) {
                return dispatchPoolService != null
                                && matchingBatchProperties != null
                                && matchingBatchProperties.isEnabled()
                                && (!matchingBatchProperties.isClientCapabilityRequired()
                                || Boolean.TRUE.equals(event.getBatchOfferEnabled()));
        }

        /**
         * ✅ Nhận lệnh từ Saga Orchestrator: Dừng tìm shipper (khi Saga timeout hoặc Order bị huỷ)
         */
        @KafkaListener(
                        topics = "saga.command.stop-matching",
                        autoStartup = "${match.kafka.stop-listener.auto-startup:${match.kafka.listener.auto-startup:true}}")
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
                        UUID stopEventId = UUID.fromString(node.get("eventId").asText());
                        Long deliveryId = node.has("deliveryId") ? node.get("deliveryId").asLong() : null;
                        Long orderId = node.has("orderId") ? node.get("orderId").asLong() : null;
                        if (!node.hasNonNull("matchingSessionId")) {
                                throw new IllegalArgumentException("stop-matching matchingSessionId is required");
                        }
                        UUID matchingSessionId = UUID.fromString(node.get("matchingSessionId").asText());
                        if (orderId == null || orderId <= 0 || deliveryId == null || deliveryId <= 0) {
                                throw new IllegalArgumentException(
                                                "stop-matching orderId and deliveryId must be positive");
                        }
                        
                        log.warn("🛑 Received STOP_MATCHING command for delivery {} generation {}",
                                        deliveryId, matchingSessionId);
                        // Persist the generation fence before touching the volatile Redis
                        // projection. Once durable, Redis failure is retried from PostgreSQL
                        // rather than exhausting the finite Kafka retry budget into a DLT.
                        matchCommandStore.recordStopMatching(
                                        stopEventId, orderId, deliveryId, matchingSessionId, message);
                        if (!cancellationProjectionRelay.projectNow(deliveryId, matchingSessionId)) {
                                log.warn("Stop-matching {} is durably fenced; Redis projection is pending recovery",
                                                stopEventId);
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
                DecisionTraceAccumulator trace = new DecisionTraceAccumulator();

                // A tombstone is monotonic for this matching generation. A
                // delayed find must not resurrect it, while a later rematch has
                // a different generation and remains eligible to proceed.
                if (matchCancellationService.isCancelled(
                                event.getDeliveryId(), matchingSessionId(event))) {
                        log.info("Matching command {} generation {} is cancelled",
                                        event.getEventId(), matchingSessionId(event));
                        return cancelCommand(event);
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

                if (matchingDeadlineReached(event)) {
                        log.info("Matching command {} reached Saga deadline before search for delivery {}",
                                        event.getEventId(), event.getDeliveryId());
                        return stageShipperNotFound(event, request, 0, true, trace);
                }

                // ✅ Reactive retry với exponential backoff
		return Mono.defer(() -> {
				if (matchingDeadlineReached(event)) {
					return Mono.<List<NearbyShipperResponse>>error(new MatchingDeadlineExceededException());
				}
				trace.recordSearchAttempt();
				trace.startStage(TraceStage.GEO_QUERY);
				return matchService.findNearbyShippers(request, systemUserId, systemRole)
						.doFinally(signal -> trace.finishStage(TraceStage.GEO_QUERY));
			})
                                // ✅ Cancel fast: if delivery already cancelled, stop chain immediately
                                .flatMap(shippers -> {
                                        if (matchCancellationService.isCancelled(
                                                        event.getDeliveryId(), matchingSessionId(event))) {
                                                return Mono.error(new MatchingCancelledException());
                                        }
                                        if (matchingDeadlineReached(event)) {
                                                return Mono.error(new MatchingDeadlineExceededException());
                                        }
                                        return Mono.just(shippers);
                                })
                .flatMap(shippers -> {
                        trace.observeGeo(shippers);
                        if (shippers != null && !shippers.isEmpty()) {
                                // ✅ Filter out excluded shippers (previously rejected this order)
                                java.util.List<Long> excluded = event.getExcludedShipperIds();
                                if (excluded != null && !excluded.isEmpty()) {
                                        List<NearbyShipperResponse> filtered = shippers.stream()
                                                .filter(s -> !excluded.contains(s.getShipperId()))
                                                .collect(java.util.stream.Collectors.toList());

                                        shippers.stream()
                                                .filter(s -> excluded.contains(s.getShipperId()))
                                                .forEach(s -> trace.markExcluded(s.getShipperId()));
                                        
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
                                .flatMap(shippers -> selectEligibleShipper(event, shippers, trace))
                                .flatMap(shippers -> {
                                        if (matchingDeadlineReached(event)) {
                                                return Mono.error(new MatchingDeadlineExceededException());
                                        }
                                        ShipperFoundEvent proposed = createShipperFoundEvent(event, shippers);
                                        return Mono.fromCallable(() -> matchCommandStore.stageCandidate(
                                                        event.getEventId(), proposed))
                                                        .subscribeOn(Schedulers.boundedElastic())
                                                        .flatMap(candidate -> {
                                                                if (candidate == null) {
                                                                        return Mono.<Void>empty();
                                                                }
                                                                return reserveAndStageCandidate(
                                                                                event, candidate, request, trace);
                                                        });
                                })
			.retryWhen(Retry.backoff(maxRetries, Duration.ofSeconds(initialDelay))
						.maxBackoff(Duration.ofSeconds(maxDelay))
						.multiplier(backoffMulti)
						.jitter(0d)
                                                .doBeforeRetry(retrySignal -> {
                                                        // ✅ Nếu đã cancel thì đừng schedule retry nữa
                                                        if (matchCancellationService.isCancelled(
                                                                        event.getDeliveryId(), matchingSessionId(event))) {
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

                                                        if (!canRetryBeforeDeadline(event, delayMs)) {
                                                                throw new MatchingDeadlineExceededException();
                                                        }

                                                        log.info("🔄 Retry attempt {}/{} for delivery: {} - Next retry in {}ms",
                                                                        attempt, maxRetries,
                                                                        event.getDeliveryId(), delayMs);
                                                })
                                                .filter(throwable -> {
                                                        // ✅ Chỉ retry nếu không tìm thấy shipper (empty result)
                                                        // Không retry nếu có lỗi system khác
                                                        return throwable instanceof NoShipperAvailableException
                                                                        && !matchingDeadlineReached(event);
                                                }))
                                .onErrorResume(error -> {
                                        if (hasCause(error, MatchingCancelledException.class)) {
                                                log.info("🛑 Matching stopped because delivery {} was cancelled",
                                                                event.getDeliveryId());
                                                return cancelCommand(event);
                                        }

                                        if (hasCause(error, MatchingDeadlineExceededException.class)) {
                                                log.info("Matching deadline reached for delivery {} after {} attempts",
                                                                event.getDeliveryId(), attemptCount.get() + 1);
                                                return stageShipperNotFound(
                                                        event, request, attemptCount.get() + 1, true, trace);
                                        }

                                        log.error("💥 Failed to find shippers for delivery: {} after {} attempts - Error: {}",
                                                        event.getDeliveryId(), maxRetries, error.getMessage());
                                        if (!hasCause(error, NoShipperAvailableException.class)) {
                                                // Propagate infrastructure failures to @RetryableTopic. The
                                                // adapter moves exhausted records to saga.command.find-shipper.DLT.
                                                return Mono.<Void>error(error);
                                        }

                                        return stageShipperNotFound(event, request, maxRetries, false, trace);
                                });
        }

        private Mono<Void> resumeStagedCandidate(
                        FindShipperEvent event,
                        ShipperFoundEvent candidate) {
                if (candidate == null) {
                        return startContinuousShipperSearch(event);
                }
                FindNearbyShippersRequest request = createFindShippersRequest(event);
                DecisionTraceAccumulator trace = new DecisionTraceAccumulator();
                trace.markResumed(candidate);
                if (matchCancellationService.isCancelled(
                                event.getDeliveryId(), matchingSessionId(event))) {
                        return cancelCommand(event);
                }
                if (matchingDeadlineReached(event)) {
                        return stageShipperNotFound(event, request, 0, true, trace);
                }
                return reserveAndStageCandidate(event, candidate, request, trace)
                                .onErrorResume(error -> {
                                        if (hasCause(error, MatchingDeadlineExceededException.class)) {
                                                return stageShipperNotFound(event, request, 0, true, trace);
                                        }
                                        if (hasCause(error, NoShipperAvailableException.class)) {
                                                return startContinuousShipperSearch(event);
                                        }
                                        return Mono.error(error);
                                });
        }

        private Mono<Void> reserveAndStageCandidate(
                        FindShipperEvent event,
                        ShipperFoundEvent candidate,
                        FindNearbyShippersRequest request,
                        DecisionTraceAccumulator trace) {
                Long shipperId = candidate.getAvailableShippers().get(0).getShipperId();
                return Mono.defer(() -> {
                        if (matchCancellationService.isCancelled(
                                        event.getDeliveryId(), matchingSessionId(event))) {
                                return cancelCommand(event);
                        }
                        if (matchingDeadlineReached(event)) {
                                return Mono.<Void>error(new MatchingDeadlineExceededException());
                        }
                        trace.startStage(TraceStage.RESERVE);
                        trace.markReservationAttempted();
                        return Mono.fromCallable(() -> matchService.tryReserveShipperOffer(
                                        shipperId, event.getDeliveryId(), matchingSessionId(event), 180))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .doFinally(signal -> trace.finishStage(TraceStage.RESERVE))
                                        .flatMap(reserved -> {
                                                if (!reserved) {
                                                        trace.markReservation(false);
                                                        return clearCandidateAndRetry(event,
                                                                        "reservation race");
                                                }
                                                if (matchCancellationService.isCancelled(
                                                                event.getDeliveryId(), matchingSessionId(event))) {
                                                        trace.markReservationReleased("Reservation released after cancellation");
                                                        return releaseCandidate(event, shipperId)
                                                                        .then(cancelCommand(event));
                                                }
                                                if (matchingDeadlineReached(event)) {
                                                        trace.markReservationReleased("Reservation released after matching deadline");
                                                        return releaseCandidate(event, shipperId)
                                                                        .then(Mono.<Void>error(
                                                                                        new MatchingDeadlineExceededException()));
                                                }
                                                return Mono.fromCallable(() -> matchCommandStore.stageFoundResult(
                                                                event.getEventId(), candidate))
                                                                .subscribeOn(Schedulers.boundedElastic())
                                                                        .flatMap(staged -> {
                                                                                if (!staged) {
                                                                                        return releaseCandidate(event, shipperId);
                                                                                }
                                                                                trace.markReservation(true);
                                                                                trace.markSelected(shipperId);
                                                                                MatchingDecisionTraceEvent decisionTrace =
                                                                                                createDecisionTrace(
                                                                                                                event,
                                                                                                                request,
                                                                                                                trace,
                                                                                                                "SHIPPER_SELECTED",
                                                                                                                shipperId,
                                                                                                                trace.attemptCount());
                                                                                persistDecisionTraceBestEffort(
                                                                                                event.getEventId(),
                                                                                                decisionTrace);
                                                                                businessMetrics.record("shipper_found");
                                                                        log.info("✅ Staged durable single-shipper result for delivery: {}",
                                                                                        event.getDeliveryId());
                                                                        return Mono.<Void>empty();
                                                                });
                                        });
                });
        }

        private Mono<Void> clearCandidateAndRetry(FindShipperEvent event, String reason) {
                return Mono.fromRunnable(() -> matchCommandStore.clearStagedCandidate(event.getEventId()))
                                .subscribeOn(Schedulers.boundedElastic())
                                .then(Mono.<Void>error(new NoShipperAvailableException(
                                                "No shippers found for delivery: " + event.getDeliveryId()
                                                                + " (" + reason + ")")));
        }

        private Mono<Void> releaseCandidate(FindShipperEvent event, Long shipperId) {
                return Mono.fromRunnable(() -> matchService.releaseShipperOffer(
                                shipperId, event.getDeliveryId(), matchingSessionId(event)))
                                .subscribeOn(Schedulers.boundedElastic())
                                .then();
        }

        private Mono<Void> cancelCommand(FindShipperEvent event) {
                return Mono.fromRunnable(() -> matchCommandStore.cancelCommand(event.getEventId()))
                                .subscribeOn(Schedulers.boundedElastic())
                                .then();
        }

        private Mono<Void> stageShipperNotFound(
                        FindShipperEvent event,
                        FindNearbyShippersRequest request,
                        int attempts,
                        boolean deadlineTerminal,
                        DecisionTraceAccumulator trace) {
                ShipperNotFoundEvent notFoundEvent = new ShipperNotFoundEvent(
                                event.getDeliveryId(), event.getOrderId(), Math.max(0, attempts));
                notFoundEvent.setEventId(outcomeEventId(
                                "shipper-not-found", event.getEventId()).toString());
                notFoundEvent.setMatchingSessionId(matchingSessionId(event).toString());
                notFoundEvent.setSearchRadius(request.getRadiusKm());
                notFoundEvent.setPickupLat(request.getLatitude());
                notFoundEvent.setPickupLng(request.getLongitude());
                return Mono.fromCallable(() -> matchCommandStore.stageNotFoundResult(
                                event.getEventId(), notFoundEvent, deadlineTerminal))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(decision -> {
                                        if (decision.stagedCandidate() != null) {
                                                return resumeStagedCandidate(event, decision.stagedCandidate());
                                        }
                                        if (decision.staged()) {
                                                MatchingDecisionTraceEvent decisionTrace =
                                                                createDecisionTrace(
                                                                                event,
                                                                                request,
                                                                                trace,
                                                                                "SHIPPER_NOT_FOUND",
                                                                                null,
                                                                                attempts);
                                                persistDecisionTraceBestEffort(
                                                                event.getEventId(), decisionTrace);
                                                businessMetrics.record("shipper_not_found");
                                                log.info("✅ Staged durable ShipperNotFoundEvent for delivery: {} after {} failed attempts",
                                                                event.getDeliveryId(), attempts);
                                        }
                                        return Mono.<Void>empty();
                                });
        }

	private boolean matchingDeadlineReached(FindShipperEvent event) {
		return event.getMatchingDeadlineAt() != null
				&& !event.getMatchingDeadlineAt().isAfter(java.time.LocalDateTime.now(clock));
        }

        private boolean canRetryBeforeDeadline(FindShipperEvent event, long delayMs) {
                if (event.getMatchingDeadlineAt() == null) {
                        return true;
                }
		return java.time.LocalDateTime.now(clock).plusNanos(delayMs * 1_000_000L)
				.isBefore(event.getMatchingDeadlineAt());
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

        private static final class MatchingDeadlineExceededException extends RuntimeException {
                private MatchingDeadlineExceededException() {
                        super("MATCHING_DEADLINE_EXCEEDED");
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
                foundEvent.setMatchingSessionId(matchingSessionId(event).toString());

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

        private UUID matchingSessionId(FindShipperEvent event) {
                // V1 commands had no explicit session; their command event ID
                // is the only safe generation identity during the rollout.
                return event.getMatchingSessionId() == null
                                ? event.getEventId()
                                : event.getMatchingSessionId();
        }

        private java.util.UUID outcomeEventId(String outcome, java.util.UUID commandEventId) {
                String identity = "match:" + outcome + ":" + commandEventId;
                return java.util.UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        }

        private Mono<List<NearbyShipperResponse>> selectEligibleShipper(
                        FindShipperEvent event,
                        List<NearbyShipperResponse> shippers,
                        DecisionTraceAccumulator trace) {
                trace.startStage(TraceStage.COD_ELIGIBILITY);
                return Flux.fromIterable(shippers)
                                .concatMap(shipper -> settlementEligibilityClient
                                                .isCodEligible(shipper.getShipperId(), event.getTotalPrice())
                                                .flatMap(eligible -> {
                                                        trace.markCodEligibility(
                                                                        shipper.getShipperId(), eligible);
                                                        return Boolean.TRUE.equals(eligible)
                                                                        ? Mono.just(shipper)
                                                                        : Mono.empty();
                                                }))
                                .next()
                                .map(List::of)
                                .switchIfEmpty(Mono.error(new NoShipperAvailableException(
                                        "No COD-eligible shipper found for delivery: " + event.getDeliveryId())))
                                .doFinally(signal -> trace.finishStage(TraceStage.COD_ELIGIBILITY));
        }

        private void persistDecisionTraceBestEffort(
                        UUID commandId,
                        MatchingDecisionTraceEvent trace) {
                try {
                        matchCommandStore.stageDecisionTrace(commandId, trace);
                } catch (RuntimeException exception) {
                        // A trace is observability only. Do not turn a durable
                        // assignment/not-found result into a business retry.
                        log.warn("Unable to persist Match decision trace for command {}: {}",
                                        commandId, exception.getMessage());
                }
        }

        private MatchingDecisionTraceEvent createDecisionTrace(
                        FindShipperEvent event,
                        FindNearbyShippersRequest request,
                        DecisionTraceAccumulator trace,
                        String decision,
                        Long selectedShipperId,
                        int attempts) {
                MatchingDecisionTraceEvent result = new MatchingDecisionTraceEvent();
                result.setEventId(outcomeEventId("decision-trace", event.getEventId()));
                result.setCommandEventId(event.getEventId());
                result.setMatchingSessionId(matchingSessionId(event).toString());
                result.setOrderId(event.getOrderId());
                result.setDeliveryId(event.getDeliveryId());
                result.setDecision(decision);
                result.setPickupLat(request.getLatitude());
                result.setPickupLng(request.getLongitude());
                result.setRadiusKm(request.getRadiusKm());
                result.setCandidatePoolSize(candidatePoolSize);
                result.setAttempts(Math.max(Math.max(0, attempts), trace.attemptCount()));
                result.setLatencyMs(trace.elapsedMs());
                result.setOccurredAt(java.time.Instant.now(clock));
                result.setSelectedShipperId(selectedShipperId);
                result.setCandidates(trace.snapshot());
                result.setNotes(new ArrayList<>(trace.notes()));

                MatchingDecisionTraceEvent.Stage geo = new MatchingDecisionTraceEvent.Stage();
                geo.setName("GEO_QUERY");
                geo.setResult(trace.geoResult());
                geo.setCandidateCount(trace.geoCandidateCount());
                geo.setLatencyMs(trace.stageLatencyMs(TraceStage.GEO_QUERY));
                geo.setDetail("Candidate list is post GEO online/fresh/busy/offer filtering");
                result.getStages().add(geo);

                MatchingDecisionTraceEvent.Stage cod = new MatchingDecisionTraceEvent.Stage();
                cod.setName("COD_ELIGIBILITY");
                cod.setResult(trace.codResult());
                cod.setCandidateCount(trace.codChecks());
                cod.setLatencyMs(trace.stageLatencyMs(TraceStage.COD_ELIGIBILITY));
                cod.setDetail("Settlement eligibility checked sequentially before reserve");
                result.getStages().add(cod);

                MatchingDecisionTraceEvent.Stage reserve = new MatchingDecisionTraceEvent.Stage();
                reserve.setName("RESERVE");
                reserve.setResult(trace.reservationResult());
                reserve.setCandidateCount(selectedShipperId == null ? 0 : 1);
                reserve.setLatencyMs(trace.stageLatencyMs(TraceStage.RESERVE));
                result.getStages().add(reserve);

                MatchingDecisionTraceEvent.Stage outcome = new MatchingDecisionTraceEvent.Stage();
                outcome.setName("OUTCOME");
                outcome.setResult(decision);
                outcome.setCandidateCount(selectedShipperId == null ? 0 : 1);
                outcome.setDetail(selectedShipperId == null
                        ? "No candidate passed the active matching path"
                        : "One candidate was selected and the durable result was staged");
                result.getStages().add(outcome);
                return result;
        }

        private enum TraceStage {
                GEO_QUERY,
                COD_ELIGIBILITY,
                RESERVE
        }

        private static final class DecisionTraceAccumulator {
                private final Map<Long, MatchingDecisionTraceEvent.Candidate> candidates =
                                new LinkedHashMap<>();
                private final List<String> notes = new ArrayList<>();
                private final Map<TraceStage, Long> stageStartedNanos = new EnumMap<>(TraceStage.class);
                private final Map<TraceStage, Long> stageElapsedNanos = new EnumMap<>(TraceStage.class);
                private final long startedNanos = System.nanoTime();
                private int geoCandidateCount;
                private int codChecks;
                private int codRejected;
                private int searchAttempts;
                private boolean geoObserved;
                private boolean geoHadCandidates;
                private boolean resumed;
                private boolean reservationAttempted;
                private boolean reservationLost;
                private boolean reservationReleased;
                private boolean reservationWon;

                void recordSearchAttempt() {
                        searchAttempts++;
                }

                int attemptCount() {
                        return searchAttempts;
                }

                long elapsedMs() {
                        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
                }

                void startStage(TraceStage stage) {
                        stageStartedNanos.put(stage, System.nanoTime());
                }

                void finishStage(TraceStage stage) {
                        Long started = stageStartedNanos.remove(stage);
                        if (started != null) {
                                stageElapsedNanos.merge(stage, System.nanoTime() - started, Long::sum);
                        }
                }

                long stageLatencyMs(TraceStage stage) {
                        return Math.max(0L, stageElapsedNanos.getOrDefault(stage, 0L) / 1_000_000L);
                }

                void observeGeo(List<NearbyShipperResponse> shippers) {
                        geoObserved = true;
                        geoCandidateCount = Math.max(geoCandidateCount, shippers == null ? 0 : shippers.size());
                        if (shippers == null) return;
                        geoHadCandidates |= !shippers.isEmpty();
                        int rank = 1;
                        for (NearbyShipperResponse shipper : shippers) {
                                if (shipper == null || shipper.getShipperId() == null) continue;
                                MatchingDecisionTraceEvent.Candidate candidate = candidates.computeIfAbsent(
                                                shipper.getShipperId(), ignored -> new MatchingDecisionTraceEvent.Candidate());
                                candidate.setShipperId(shipper.getShipperId());
                                candidate.setLatitude(shipper.getLatitude());
                                candidate.setLongitude(shipper.getLongitude());
                                candidate.setDistanceKm(shipper.getDistanceKm());
                                candidate.setOnline(shipper.isOnline());
                                candidate.setRank(rank++);
                                if (!"REJECTED".equals(candidate.getState())) {
                                        candidate.setState("GEO_ELIGIBLE");
                                }
                        }
                }

                void markExcluded(Long shipperId) {
                        MatchingDecisionTraceEvent.Candidate candidate = candidates.get(shipperId);
                        if (candidate != null) {
                                candidate.setState("REJECTED");
                                addReason(candidate, "EXCLUDED_BY_SAGA");
                        }
                }

                void markCodEligibility(Long shipperId, Boolean eligible) {
                        codChecks++;
                        MatchingDecisionTraceEvent.Candidate candidate = candidates.get(shipperId);
                        if (candidate == null) return;
                        candidate.setCodEligible(eligible);
                        if (!Boolean.TRUE.equals(eligible)) {
                                codRejected++;
                                candidate.setState("REJECTED");
                                addReason(candidate, "COD_NOT_ELIGIBLE");
                        } else if (!"REJECTED".equals(candidate.getState())) {
                                candidate.setState("COD_ELIGIBLE");
                        }
                }

                void markResumed(ShipperFoundEvent candidateEvent) {
                        resumed = true;
                        notes.add("Resumed a durable candidate staged by an earlier Match attempt");
                        if (candidateEvent == null || candidateEvent.getAvailableShippers() == null
                                        || candidateEvent.getAvailableShippers().isEmpty()) {
                                return;
                        }
                        ShipperFoundEvent.ShipperMatchResult source = candidateEvent.getAvailableShippers().get(0);
                        if (source == null || source.getShipperId() == null) return;
                        MatchingDecisionTraceEvent.Candidate candidate = candidates.computeIfAbsent(
                                        source.getShipperId(), ignored -> new MatchingDecisionTraceEvent.Candidate());
                        candidate.setShipperId(source.getShipperId());
                        candidate.setLatitude(source.getLatitude());
                        candidate.setLongitude(source.getLongitude());
                        candidate.setDistanceKm(source.getDistanceKm());
                        candidate.setOnline(source.getIsOnline());
                        candidate.setRank(1);
                        candidate.setState("STAGED_REPLAY");
                        addReason(candidate, "RESUMED_FROM_DURABLE_CANDIDATE");
                        geoObserved = true;
                        geoHadCandidates = true;
                        geoCandidateCount = Math.max(geoCandidateCount, 1);
                }

                void markReservationAttempted() {
                        reservationAttempted = true;
                }

                void markReservation(boolean won) {
                        reservationWon = won;
                        reservationLost = !won;
                        if (!won) addNote("Reservation lost a concurrent ownership race");
                }

                void markReservationReleased(String reason) {
                        reservationReleased = true;
                        addNote(reason);
                }

                void markSelected(Long shipperId) {
                        MatchingDecisionTraceEvent.Candidate candidate = candidates.get(shipperId);
                        if (candidate != null) candidate.setState("SELECTED");
                }

                List<MatchingDecisionTraceEvent.Candidate> snapshot() {
                        return new ArrayList<>(candidates.values());
                }

                String geoResult() {
                        if (resumed) return "RESUMED";
                        if (!geoObserved) return "NOT_RUN";
                        return geoHadCandidates ? "OBSERVED" : "EMPTY";
                }

                String codResult() {
                        if (codChecks == 0) return "NOT_RUN";
                        return codRejected > 0 ? "FILTERED" : "PASSED";
                }

                String reservationResult() {
                        if (reservationWon) return "WON";
                        if (reservationReleased) return "RELEASED";
                        if (reservationLost) return "LOST";
                        return reservationAttempted ? "NOT_CONFIRMED" : "NOT_RUN";
                }

                private void addNote(String note) {
                        if (!notes.contains(note)) notes.add(note);
                }

                private void addReason(MatchingDecisionTraceEvent.Candidate candidate, String reason) {
                        if (candidate != null && !candidate.getReasons().contains(reason)) {
                                candidate.getReasons().add(reason);
                        }
                }

                List<String> notes() { return notes; }
                int geoCandidateCount() { return geoCandidateCount; }
                int codChecks() { return codChecks; }
                int codRejected() { return codRejected; }
                boolean reservationWon() { return reservationWon; }
        }
}
