package com.delivery.order_service.client;

import com.delivery.order_service.dto.response.RestaurantResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestaurantClient {
    
    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${restaurant.service.url:http://localhost:8083}")
    private String restaurantServiceUrl;
    
    /**
     * Lấy danh sách nhà hàng theo creator ID
     */
    @SuppressWarnings("unchecked")
    public List<RestaurantResponse> getRestaurantsByCreatorId(Long creatorId) {
        try {
            String url = restaurantServiceUrl + "/api/restaurants/creator/" + creatorId;
            
            log.info("🔍 Calling Restaurant Service: {}", url);
            
            // Get response as Map to handle generic types
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.get("data") != null) {
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");
                
                // Convert to RestaurantResponse list
                List<RestaurantResponse> restaurants = objectMapper.convertValue(
                    dataList, 
                    new TypeReference<List<RestaurantResponse>>() {}
                );
                
                log.info("✅ Found {} restaurants for creator {}", restaurants.size(), creatorId);
                return restaurants;
            }
            
            log.warn("⚠️ No restaurants found for creator {}", creatorId);
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.error("❌ Failed to get restaurants for creator {}: {}", creatorId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * ✅ Lấy thông tin chi tiết nhà hàng theo ID (dùng WebClient Mono - Non-blocking)
     * Sử dụng khi tạo đơn hàng để validate và lấy creatorId
     */
    public Mono<RestaurantResponse> getRestaurantById(Long restaurantId) {
        String url = restaurantServiceUrl + "/api/restaurants/" + restaurantId;
        
        log.info("🔍 [WebClient] Calling Restaurant Service to validate: {}", url);
        
        return webClient
                .get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseRestaurantResponse)
                .doOnSuccess(restaurant -> {
                    if (restaurant != null) {
                        log.info("✅ Restaurant validated: id={}, name={}, creatorId={}", 
                                restaurant.getId(), restaurant.getName(), restaurant.getCreatorId());
                    }
                })
                .doOnError(error -> {
                    log.error("❌ Failed to validate restaurant {}: {}", restaurantId, error.getMessage());
                })
                .onErrorReturn(createErrorRestaurantResponse(restaurantId));
    }
    
    /**
     * Parse JSON response thành RestaurantResponse
     */
    @SuppressWarnings("unchecked")
    private RestaurantResponse parseRestaurantResponse(String responseBody) {
        try {
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            
            if (response != null && response.get("data") != null) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                return objectMapper.convertValue(data, RestaurantResponse.class);
            }
            
            log.warn("⚠️ No data found in response");
            return null;
            
        } catch (Exception e) {
            log.error("❌ Failed to parse restaurant response: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Tạo response lỗi khi không lấy được thông tin nhà hàng
     */
    private RestaurantResponse createErrorRestaurantResponse(Long restaurantId) {
        RestaurantResponse errorResponse = new RestaurantResponse();
        errorResponse.setId(restaurantId);
        errorResponse.setName("Unknown Restaurant");
        return errorResponse;
    }
}
