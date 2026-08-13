package com.delivery.match_service.service.impl;

import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import com.delivery.match_service.repository.MatchRedisGeoRepository;
import com.delivery.match_service.service.MatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ✅ Match Service Implementation — Event-driven, không gọi REST
 * Sử dụng local Redis Geo replica thay vì WebClient call tracking-service
 */
@Service
@Slf4j
public class MatchServiceImpl implements MatchService {

    private final MatchRedisGeoRepository matchRedisGeoRepository;

    public MatchServiceImpl(MatchRedisGeoRepository matchRedisGeoRepository) {
        this.matchRedisGeoRepository = matchRedisGeoRepository;
    }

    /**
     * ✅ Tìm shipper gần — query 100% local Redis Geo, KHÔNG gọi REST
     * Dữ liệu được replicate từ tracking-service qua Kafka topic "shipper.location-updated"
     */
    @Override
    public Mono<List<NearbyShipperResponse>> findNearbyShippers(FindNearbyShippersRequest request, Long userId,
            String role) {
        return Mono.fromCallable(() -> {
            log.info("🔍 [Local Geo] Finding nearby shippers at ({}, {}) radius={}km",
                    request.getLatitude(), request.getLongitude(), request.getRadiusKm());

            List<MatchRedisGeoRepository.NearbyShipperResult> results =
                    matchRedisGeoRepository.findNearbyShippers(
                            request.getLatitude(),
                            request.getLongitude(),
                            request.getRadiusKm(),
                            request.getMaxShippers());

            // Convert sang NearbyShipperResponse để giữ tương thích với FindShipperEventListener
            List<NearbyShipperResponse> responses = results.stream()
                    .map(r -> new NearbyShipperResponse(
                            r.shipperId,
                            null, // Match owns location/availability, not profile identity.
                            null,
                            r.latitude,
                            r.longitude,
                            r.distanceKm,
                            true,  // đã filter online trong repo
                            null))
                    .collect(Collectors.toList());

            log.info("✅ [Local Geo] Found {} available shippers (no REST call)", responses.size());
            return responses;

        }).subscribeOn(Schedulers.boundedElastic()); // Redis IO trên thread pool riêng
    }

    @Override
    public boolean tryReserveShipperOffer(
            Long shipperId,
            Long deliveryId,
            UUID matchingSessionId,
            int timeoutSeconds) {
        return matchRedisGeoRepository.tryReserveShipperOffer(
                shipperId, deliveryId, matchingSessionId, timeoutSeconds);
    }

    @Override
    public boolean releaseShipperOffer(Long shipperId, Long deliveryId, UUID matchingSessionId) {
        return matchRedisGeoRepository.releaseShipperOffer(shipperId, deliveryId, matchingSessionId);
    }

}
