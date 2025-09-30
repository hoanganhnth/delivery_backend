package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.ApiPathConstants;
import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResultResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.OrderCacheValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    
    /**
     * API validate order với OrderValidationRequest format từ order-service
     * Endpoint: POST /api/restaurants/validate/order
     */
    @PostMapping("/order")
    public ResponseEntity<BaseResponse<OrderValidationResultResponse>> validateOrder(
            @RequestBody OrderValidationRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        
        log.info("🔍 Validating order for restaurant: {} by user: {}", request.getRestaurantId(), userId);
        
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
    
    /**
     * API validate single menu item (helper method)
     * Endpoint: POST /api/restaurants/validate/menu-item
     */
    @PostMapping("/menu-item")
    public ResponseEntity<BaseResponse<Boolean>> validateMenuItem(
            @RequestParam Long restaurantId,
            @RequestParam Long menuItemId,
            @RequestParam Integer quantity,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        
        log.info("🔍 Validating single menu item: {} for restaurant: {}", menuItemId, restaurantId);
        
        // Tạo OrderItemRequest để validate
        OrderValidationRequest.OrderItemRequest item = OrderValidationRequest.OrderItemRequest.builder()
                .menuItemId(menuItemId)
                .quantity(quantity)
                .build();
        
        OrderValidationResultResponse.ItemValidationInfo validation = 
                orderCacheValidationService.validateMenuItem(restaurantId, item);
        
        boolean isValid = validation.getIsAvailable() && validation.getHasEnoughStock();
        
        return ResponseEntity.ok(new BaseResponse<>(
                isValid ? 1 : 0,
                isValid,
                isValid ? "Menu item hợp lệ" : "Menu item không hợp lệ"));
    }
    
    /**
     * API calculate order total từ OrderValidationRequest
     * Endpoint: POST /api/restaurants/validate/calculate-total
     */
    @PostMapping("/calculate-total")
    public ResponseEntity<BaseResponse<Double>> calculateOrderTotal(
            @RequestBody OrderValidationRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        
        log.info("🧮 Calculating total for restaurant: {} with {} items", 
                request.getRestaurantId(), request.getItems().size());
        
        Double total = orderCacheValidationService.calculateTotalFromItems(request);
        
        return ResponseEntity.ok(new BaseResponse<>(1, total, "Tính tổng tiền thành công"));
    }
    
    /**
     * API check restaurant operating hours
     * Endpoint: GET /api/restaurants/validate/operating-hours/{restaurantId}
     */
    @GetMapping("/operating-hours/{restaurantId}")
    public ResponseEntity<BaseResponse<Boolean>> checkOperatingHours(
            @PathVariable Long restaurantId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        
        log.info("🕐 Checking operating hours for restaurant: {}", restaurantId);
        
        boolean isOpen = orderCacheValidationService.isRestaurantAvailable(restaurantId);
        
        return ResponseEntity.ok(new BaseResponse<>(
                isOpen ? 1 : 0,
                isOpen,
                isOpen ? "Restaurant đang mở cửa" : "Restaurant đang đóng cửa"));
    }
}
