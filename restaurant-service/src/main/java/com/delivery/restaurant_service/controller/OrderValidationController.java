package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.ApiPathConstants;
import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.dto.request.ValidateOrderRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.OrderValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller tạm thời cho Order Validation
 * Thiết kế để dễ dàng di chuyển sang Catalog Service
 */
@RestController
@RequestMapping(ApiPathConstants.RESTAURANTS + "/validate")
@RequiredArgsConstructor
public class OrderValidationController {
    
    private final OrderValidationService orderValidationService;
    
    /**
     * API validate order trước khi tạo
     * Endpoint: POST /api/restaurants/validate/order
     */
    @PostMapping("/order")
    public ResponseEntity<BaseResponse<OrderValidationResponse>> validateOrder(
            @RequestBody ValidateOrderRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        
        OrderValidationResponse response = orderValidationService.validateOrder(request);
        
        String message = response.getIsValid() ? 
                "Order validation thành công" : 
                "Order validation thất bại";
        
        return ResponseEntity.ok(new BaseResponse<>(
                response.getIsValid() ? 1 : 0, 
                response, 
                message));
    }
    
    /**
     * API validate single menu item
     * Endpoint: POST /api/restaurants/validate/menu-item
     */
    @PostMapping("/menu-item")
    public ResponseEntity<BaseResponse<Boolean>> validateMenuItem(
            @RequestParam Long restaurantId,
            @RequestParam Long menuItemId,
            @RequestParam Integer quantity,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        
        boolean isValid = orderValidationService.validateMenuItem(restaurantId, menuItemId, quantity);
        
        return ResponseEntity.ok(new BaseResponse<>(
                isValid ? 1 : 0,
                isValid,
                isValid ? "Menu item hợp lệ" : "Menu item không hợp lệ"));
    }
    
    /**
     * API calculate order total
     * Endpoint: POST /api/restaurants/validate/calculate-total
     */
    @PostMapping("/calculate-total")
    public ResponseEntity<BaseResponse<Double>> calculateOrderTotal(
            @RequestBody ValidateOrderRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        
        Double total = orderValidationService.calculateOrderTotal(request);
        
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
        
        boolean isOpen = orderValidationService.validateRestaurantOperatingHours(restaurantId);
        
        return ResponseEntity.ok(new BaseResponse<>(
                isOpen ? 1 : 0,
                isOpen,
                isOpen ? "Restaurant đang mở cửa" : "Restaurant đang đóng cửa"));
    }
}
