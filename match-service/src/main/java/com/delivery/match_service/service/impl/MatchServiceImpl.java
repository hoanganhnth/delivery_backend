package com.delivery.match_service.service.impl;

import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import com.delivery.match_service.service.MatchService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * ✅ Match Service Implementation
 * Theo Backend Instructions: Constructor injection pattern
 */
@Service
public class MatchServiceImpl implements MatchService {
    
    private final WebClient trackingServiceWebClient;
    
    /**
     * ✅ Constructor Injection thay vì @Autowired field injection
     * Theo Backend Instructions: Constructor injection là REQUIRED
     */
    public MatchServiceImpl(WebClient trackingServiceWebClient) {
        this.trackingServiceWebClient = trackingServiceWebClient;
    }
    
    /**
     * ✅ Call Tracking Service để lấy nearby shippers
     * Sử dụng WebClient để call microservice khác
     */
    @Override
    public List<NearbyShipperResponse> findNearbyShippers(FindNearbyShippersRequest request) {
        try {
            // ✅ Call tracking service API endpoint
            var response = trackingServiceWebClient
                    .post()
                    .uri("/api/tracking/nearby-shippers")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<NearbyShipperResponse>>() {})
                    .block(); // Blocking call cho simplicity
            
            return response != null ? response : List.of();
            
        } catch (Exception ex) {
            // Log error và return empty list thay vì throw exception
            System.err.println("Error calling tracking service: " + ex.getMessage());
            return List.of();
        }
    }
}
