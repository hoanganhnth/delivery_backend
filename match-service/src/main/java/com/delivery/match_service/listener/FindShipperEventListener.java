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
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

/**
 * ✅ Kafka Event Listener cho Match Service theo Backend Instructions
 * Lắng nghe FindShipperEvent từ Delivery Service và chỉ publish ShipperFoundEvent
 * Simplified: Chỉ dùng 1 event duy nhất cho dễ quản lý
 * ✅ Retry mechanism: Tìm shipper liên tục nếu chưa tìm thấy
 */
@Slf4j
@Component
public class FindShipperEventListener {

    private final MatchService matchService;
    private final MatchEventPublisher matchEventPublisher;
        private final MatchCancellationService matchCancellationService;

    // ✅ Retry configuration constants
    private static final int MAX_RETRY_ATTEMPTS = 10; // Tối đa 10 lần retry
    private static final int INITIAL_DELAY_SECONDS = 30; // Bắt đầu với 30 giây
    private static final int MAX_DELAY_SECONDS = 300; // Tối đa 5 phút
    private static final double BACKOFF_MULTIPLIER = 1.5; // Tăng delay theo exponential

    // ✅ Constructor Injection Pattern (MANDATORY)
        public FindShipperEventListener(
                        MatchService matchService,
                        MatchEventPublisher matchEventPublisher,
                        MatchCancellationService matchCancellationService) {
        this.matchService = matchService;
        this.matchEventPublisher = matchEventPublisher;
                this.matchCancellationService = matchCancellationService;
    }

    /**
     * ✅ Lắng nghe FindShipperEvent và tìm shipper liên tục với retry mechanism
     */
    @KafkaListener(topics = KafkaTopicConstants.FIND_SHIPPER_TOPIC)
    public void handleFindShipperEvent(
            @Payload FindShipperEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            log.info("📥 Received FindShipperEvent for delivery: {} from topic: {} partition: {} timestamp: {}",
                    event.getDeliveryId(), topic, partition, timestamp);

            // ✅ Validate event data
            if (event.getDeliveryId() == null) {
                log.error("💥 Invalid FindShipperEvent: deliveryId is null");
                acknowledgment.acknowledge();
                return;
            }

            // ✅ Start continuous shipper search with retry mechanism
            startContinuousShipperSearch(event, acknowledgment);

        } catch (Exception e) {
            log.error("🔥 Unexpected error processing FindShipperEvent for delivery: {} - Error: {}",
                    event.getDeliveryId(), e.getMessage(), e);

            // ✅ Acknowledge to prevent blocking
            acknowledgment.acknowledge();
        }
    }

    /**
     * ✅ Tìm shipper liên tục với exponential backoff retry
     */
    private void startContinuousShipperSearch(FindShipperEvent event, Acknowledgment acknowledgment) {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // ✅ New search session => clear cancel flag (idempotent)
        matchCancellationService.clearCancelled(event.getDeliveryId());

        // ✅ Convert event to request một lần
        FindNearbyShippersRequest request = createFindShippersRequest(event);
        Long systemUserId = 1L;
        String systemRole = "SYSTEM";

        // ✅ Reactive retry với exponential backoff
                matchService.findNearbyShippers(request, systemUserId, systemRole)
                                // ✅ Cancel fast: if delivery already cancelled, stop chain immediately
                                .flatMap(shippers -> {
                                        if (matchCancellationService.isCancelled(event.getDeliveryId())) {
                                                return Mono.error(new RuntimeException("DELIVERY_CANCELLED"));
                                        }
                                        return Mono.just(shippers);
                                })
                .flatMap(shippers -> {
                    if (shippers != null && !shippers.isEmpty()) {
                        // ✅ Tìm thấy shipper, trả về kết quả
                        return Mono.just(shippers);
                    } else {
                        // ✅ Không tìm thấy shipper, trigger retry
                        return Mono.error(
                                new RuntimeException("No shippers found for delivery: " + event.getDeliveryId()));
                    }
                })
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofSeconds(INITIAL_DELAY_SECONDS))
                        .maxBackoff(Duration.ofSeconds(MAX_DELAY_SECONDS))
                        .multiplier(BACKOFF_MULTIPLIER)
                        .doBeforeRetry(retrySignal -> {
                                                        // ✅ Nếu đã cancel thì đừng schedule retry nữa
                                                        if (matchCancellationService.isCancelled(event.getDeliveryId())) {
                                                                throw new RuntimeException("DELIVERY_CANCELLED");
                                                        }

                            int attempt = attemptCount.incrementAndGet();
                            long delayMs = retrySignal.totalRetries() == 0 ? INITIAL_DELAY_SECONDS * 1000
                                    : Math.min(
                                            (long) (INITIAL_DELAY_SECONDS
                                                    * Math.pow(BACKOFF_MULTIPLIER, retrySignal.totalRetries()) * 1000),
                                            MAX_DELAY_SECONDS * 1000);

                            log.info("🔄 Retry attempt {}/{} for delivery: {} - Next retry in {}ms",
                                    attempt, MAX_RETRY_ATTEMPTS, event.getDeliveryId(), delayMs);
                        })
                                                .filter(throwable -> {
                            // ✅ Chỉ retry nếu không tìm thấy shipper (empty result)
                            // Không retry nếu có lỗi system khác
                                                        if (!(throwable instanceof RuntimeException)) {
                                                                return false;
                                                        }

                                                        String msg = throwable.getMessage();
                                                        if (msg == null) {
                                                                return false;
                                                        }

                                                        // ✅ never retry when cancelled
                                                        if (msg.contains("DELIVERY_CANCELLED")) {
                                                                return false;
                                                        }

                                                        return msg.contains("No shippers found");
                        }))
                .subscribe(
                        shippers -> {
                                                        if (matchCancellationService.isCancelled(event.getDeliveryId())) {
                                                                log.info("🛑 Delivery {} cancelled while matching; skip publish found event", event.getDeliveryId());
                                                                acknowledgment.acknowledge();
                                                                return;
                                                        }

                            log.info("✅ Found {} nearby shippers for delivery: {} after {} attempts",
                                    shippers.size(), event.getDeliveryId(), attemptCount.get() + 1);

                            // ✅ Chỉ bắn ShipperFoundEvent - cả delivery-service và notification-service sẽ listen
                            ShipperFoundEvent foundEvent = createShipperFoundEvent(event, shippers);
                            matchEventPublisher.publishShipperFoundEvent(foundEvent);

                            log.info("✅ Published ShipperFoundEvent for delivery: {} with {} shippers - both delivery-service and notification-service will handle", 
                                    event.getDeliveryId(), shippers.size());

                            // ✅ Acknowledge after successful processing
                            acknowledgment.acknowledge();
                        },
                        error -> {
                                                        if (error != null && error.getMessage() != null && error.getMessage().contains("DELIVERY_CANCELLED")) {
                                                                log.info("🛑 Matching stopped because delivery {} was cancelled", event.getDeliveryId());
                                                                acknowledgment.acknowledge();
                                                                return;
                                                        }

                            log.error("💥 Failed to find shippers for delivery: {} after {} attempts - Error: {}",
                                    event.getDeliveryId(), MAX_RETRY_ATTEMPTS, error.getMessage());

                            // ✅ Bắn ShipperNotFoundEvent cho delivery-service và order-service
                            ShipperNotFoundEvent notFoundEvent = new ShipperNotFoundEvent(
                                    event.getDeliveryId(), 
                                    event.getOrderId(), 
                                    MAX_RETRY_ATTEMPTS
                            );
                            notFoundEvent.setSearchRadius(request.getRadiusKm());
                            notFoundEvent.setPickupLat(request.getLatitude());
                            notFoundEvent.setPickupLng(request.getLongitude());
                            
                            matchEventPublisher.publishShipperNotFoundEvent(notFoundEvent);

                            log.info("✅ Published ShipperNotFoundEvent for delivery: {} after {} failed attempts", 
                                    event.getDeliveryId(), MAX_RETRY_ATTEMPTS);

                            // ✅ Acknowledge even after failure to avoid infinite processing
                            acknowledgment.acknowledge();
                        });
    }

    /**
     * ✅ Convert FindShipperEvent to FindNearbyShippersRequest với null safety
     */
    private FindNearbyShippersRequest createFindShippersRequest(FindShipperEvent event) {
        FindNearbyShippersRequest request = new FindNearbyShippersRequest();

        // ✅ Null safety check for pickup coordinates
        if (event.getPickupLat() != null && event.getPickupLng() != null) {
            // Sử dụng pickup location để tìm shipper gần restaurant
            request.setLatitude(event.getPickupLat());
            request.setLongitude(event.getPickupLng());

            log.debug("🎯 Using pickup location: {}, {} for delivery: {}",
                    event.getPickupLat(), event.getPickupLng(), event.getDeliveryId());
        } else if (event.getDeliveryLat() != null && event.getDeliveryLng() != null) {
            // Fallback: sử dụng delivery location nếu pickup location null
            request.setLatitude(event.getDeliveryLat());
            request.setLongitude(event.getDeliveryLng());

            log.warn("⚠️ Pickup location null, using delivery location: {}, {} for delivery: {}",
                    event.getDeliveryLat(), event.getDeliveryLng(), event.getDeliveryId());
        } else {
            // Default location (TP.HCM center) nếu cả 2 đều null
            request.setLatitude(10.762622); // Landmark 81 coordinates
            request.setLongitude(106.660172);

            log.error("🔥 Both pickup and delivery coordinates are null for delivery: {}, using default location",
                    event.getDeliveryId());
        }

        // Default search parameters
        request.setRadiusKm(5.0); // 5km radius
        request.setMaxShippers(10); // Tối đa 10 shippers

        return request;
    }
    
    /**
     * ✅ Convert tìm được shippers thành ShipperFoundEvent với đầy đủ thông tin
     */
    private ShipperFoundEvent createShipperFoundEvent(FindShipperEvent event, List<NearbyShipperResponse> shippers) {
        List<ShipperFoundEvent.ShipperMatchResult> matchResults = shippers.stream()
                .map(shipper -> new ShipperFoundEvent.ShipperMatchResult(
                        shipper.getShipperId(),
                        shipper.getShipperName() != null ? shipper.getShipperName() : "Shipper " + shipper.getShipperId(),
                        shipper.getShipperPhone() != null ? shipper.getShipperPhone() : "N/A",
                        shipper.getDistanceKm(),
                        shipper.getLatitude(),
                        shipper.getLongitude(),
                        5.0, // Default rating
                        shipper.isOnline()
                ))
                .collect(java.util.stream.Collectors.toList());
        
        // ✅ Tạo ShipperFoundEvent với đầy đủ thông tin cho cả delivery-service và notification-service
        ShipperFoundEvent foundEvent = new ShipperFoundEvent(event.getDeliveryId(), event.getOrderId(), matchResults);
        
        // ✅ Set additional info từ FindShipperEvent
        foundEvent.setRestaurantName(event.getRestaurantName());
        foundEvent.setPickupAddress(event.getPickupAddress());
        foundEvent.setDeliveryAddress(event.getDeliveryAddress());
        foundEvent.setPickupLat(event.getPickupLat());
        foundEvent.setPickupLng(event.getPickupLng());
        foundEvent.setDeliveryLat(event.getDeliveryLat());
        foundEvent.setDeliveryLng(event.getDeliveryLng());
        
        return foundEvent;
    }
}
