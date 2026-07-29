package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.ApiPathConstants;
import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResultResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.OrderCacheValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller cho Order Validation sử dụng OrderValidationRequest format
 * Tích hợp với OrderCacheValidationService để validate từ Redis cache
 */
@RestController
@RequestMapping(ApiPathConstants.RESTAURANTS + "/validate")
@RequiredArgsConstructor
@Slf4j
public class OrderValidationController {
    
    private final OrderCacheValidationService orderCacheValidationService;

    @Value("${app.internal.secret:}")
    private String internalSecret;
    
    /**
     * API validate order với OrderValidationRequest format từ order-service
     * Endpoint: POST /api/restaurants/validate/order
     */
    @PostMapping("/order")
    public ResponseEntity<BaseResponse<OrderValidationResultResponse>> validateOrder(
            @RequestBody OrderValidationRequest request,
            @RequestHeader(value = HttpHeaderConstants.INTERNAL_TOKEN, required = false) String internalToken) {

        if (!isInternalRequest(internalToken)) {
            return forbidden();
        }
        
        log.info("🔍 Validating order for restaurant: {}", request.getRestaurantId());
        
        OrderValidationResultResponse response = 
                orderCacheValidationService.validateOrderFromOrderService(request);
        
        String message = response.getIsValid() ? 
                "Order validation thành công" : 
                "Order validation thất bại";
        
        return ResponseEntity.ok(new BaseResponse<>(
                response.getIsValid() ? 1 : 0, 
                response, 
                message));
    }
    
    private boolean isInternalRequest(String internalToken) {
        return internalSecret != null && !internalSecret.isBlank()
                && internalSecret.equals(internalToken);
    }

    private <T> ResponseEntity<BaseResponse<T>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new BaseResponse<>(0, null, "Forbidden"));
    }
}
