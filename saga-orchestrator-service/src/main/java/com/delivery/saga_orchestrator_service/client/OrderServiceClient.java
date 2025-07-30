package com.delivery.saga_orchestrator_service.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.delivery.saga_orchestrator_service.dto.common.BaseResponse;
import com.delivery.saga_orchestrator_service.dto.order.CreateOrderRequest;
import com.delivery.saga_orchestrator_service.dto.order.OrderResponse;

public class OrderServiceClient {
    private static final String ORDER_SERVICE_URL = "http://localhost:8084/api/orders";
    private static final String HEADER_X_USER_ID = "X-User-Id";
    private static final String HEADER_X_ROLE = "X-Role";
    
    private final RestTemplate restTemplate = new RestTemplate();

    public OrderResponse createOrder(CreateOrderRequest request, Long userId, String role) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Thêm required headers
            if (userId != null) {
                headers.set(HEADER_X_USER_ID, userId.toString());
            }
            if (role != null) {
                headers.set(HEADER_X_ROLE, role);
            }

            // Gói cả headers và body vào HttpEntity
            HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, headers);

            // Sử dụng ParameterizedTypeReference để handle BaseResponse<OrderResponse>
            ParameterizedTypeReference<BaseResponse<OrderResponse>> responseType = 
                new ParameterizedTypeReference<BaseResponse<OrderResponse>>() {};

            // Gửi POST request và nhận BaseResponse<OrderResponse>
            ResponseEntity<BaseResponse<OrderResponse>> response = restTemplate.exchange(
                    ORDER_SERVICE_URL, HttpMethod.POST, entity, responseType);  

            // Trả về OrderResponse từ data field của BaseResponse
            System.out.println("Response status code: " + response.getStatusCode());
            BaseResponse<OrderResponse> baseResponse = response.getBody();
            
            if (baseResponse != null && baseResponse.getStatus() == 1) {
                return baseResponse.getData();
            } else {
                System.err.println("Order creation failed: " + 
                    (baseResponse != null ? baseResponse.getMessage() : "Unknown error"));
                return null;
            }
        } catch (Exception e) {
            System.err.println("Error creating order: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

}
