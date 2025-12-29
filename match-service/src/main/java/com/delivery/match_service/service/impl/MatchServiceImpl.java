package com.delivery.match_service.service.impl;

import com.delivery.match_service.common.constants.HttpHeaderConstants;
import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import com.delivery.match_service.dto.response.TrackingServiceResponse;
import com.delivery.match_service.service.MatchCancellationService;
import com.delivery.match_service.service.MatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * ✅ Match Service Implementation với Non-blocking WebFlux
 * Theo Backend Instructions: Constructor injection pattern
 */
@Service
@Slf4j
public class MatchServiceImpl implements MatchService {

    private final WebClient trackingServiceWebClient;
    private final MatchCancellationService matchCancellationService;

    /**
     * ✅ Constructor Injection thay vì @Autowired field injection
     */
    public MatchServiceImpl(WebClient trackingServiceWebClient,
            MatchCancellationService matchCancellationService) {
        this.trackingServiceWebClient = trackingServiceWebClient;
        this.matchCancellationService = matchCancellationService;
    }

    /**
     * ✅ Non-blocking call Tracking Service với GET method và flexible response
     * handling
     * Sử dụng Mono<List> thay vì blocking call, handle both JSON and text/plain
     */
    @Override
    public Mono<List<NearbyShipperResponse>> findNearbyShippers(FindNearbyShippersRequest request, Long userId,
            String role) {
        return trackingServiceWebClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/tracking/shipper-locations/nearby")
                        .queryParam("lat", request.getLatitude())
                        .queryParam("lng", request.getLongitude())
                        .queryParam("radiusKm", request.getRadiusKm())
                        .queryParam("limit", request.getMaxShippers())
                        .build())
                .header(HttpHeaderConstants.X_USER_ID, userId != null ? userId.toString() : "")
                .header(HttpHeaderConstants.X_ROLE, role != null ? role : "")
                .header("Accept", "application/json, text/plain, */*")
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> {
                            System.err.println("HTTP Error: " + response.statusCode());
                            return Mono.error(new RuntimeException("Tracking service error: " + response.statusCode()));
                        })
                .bodyToMono(String.class) // Get raw response as String first
                .map(rawResponse -> {

                    // ✅ Try to parse as JSON using Gson (available in dependencies)
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    TrackingServiceResponse response = gson.fromJson(rawResponse, TrackingServiceResponse.class);

                    if (response != null && response.getStatus() == 1 && response.getData() != null) {
                        return response.getData();
                    }
                    return List.<NearbyShipperResponse>of();

                })
                .onErrorReturn(List.of()) // Return empty list nếu có lỗi
                .doOnError(ex -> System.err.println("Error calling tracking service: " + ex.getMessage()));
    }

    /**
     * ✅ Dừng quá trình matching cho delivery bị hủy
     * TODO: Implement Redis cleanup và notification logic
     */
    @Override
    public void stopMatchingProcess(Long deliveryId, Long orderId, String reason) {
        try {
            log.info("🛑 Stopping matching process for delivery: {}, order: {}, reason: {}", 
                    deliveryId, orderId, reason);

            // ✅ Redis-based cancel flag; FindShipperEventListener will check this and stop retry.
            matchCancellationService.markCancelled(deliveryId);
            log.info("🧹 Marked delivery as cancelled in Redis for delivery: {}", deliveryId);
            
            // Generate matching session ID (consistent với delivery-service)
            String matchingSessionId = "delivery_" + deliveryId;
            
            // TODO: Implement these features:
            // 1. Remove matching session từ Redis (nếu có)
            // 2. Cancel scheduled matching tasks 
            // 3. Notify shippers về việc hủy (nếu có ongoing matching)
            // 4. Log cancellation event
            
            log.info("✅ Successfully stopped matching process for delivery: {} with session: {}", 
                    deliveryId, matchingSessionId);
                    
        } catch (Exception e) {
            log.error("💥 Error stopping matching process for delivery: {}: {}", deliveryId, e.getMessage(), e);
        }
    }
}
