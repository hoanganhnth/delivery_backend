package com.delivery.order_service.service;

import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * ✅ Service validation cho Order theo Backend Instructions
 */
@Slf4j
@Service
public class OrderValidationService {

    /**
     * Validate comprehensive business rules cho CreateOrderRequest
     */
    public void validateCreateOrderRequest(CreateOrderRequest request, Long userId) {
        List<String> errors = new ArrayList<>();
        
        // 1. Validate basic required fields
        validateRequiredFields(request, errors);
        
        // 2. Validate business logic
        validateBusinessRules(request, errors);
        
        // 3. Validate coordinates consistency
        validateCoordinates(request, errors);
        
        // 4. Validate financial data
        validateFinancialData(request, errors);
        
        // 5. Validate user context
        validateUserContext(request, userId, errors);
        
        if (!errors.isEmpty()) {
            String errorMessage = "Dữ liệu đơn hàng không hợp lệ: " + String.join(", ", errors);
            log.error("🚨 Order validation failed for user {}: {}", userId, errorMessage);
            throw new ValidationException(errorMessage);
        }
        
        log.info("✅ Order validation passed for user: {}", userId);
    }
    
    /**
     * Validate required fields consistency
     */
    private void validateRequiredFields(CreateOrderRequest request, List<String> errors) {
        // Restaurant info validation
        if (request.getRestaurantId() == null) {
            errors.add("Restaurant ID không được để trống");
        } else if (request.getRestaurantId() <= 0) {
            errors.add("Restaurant ID phải là số dương");
        }
        
        // Restaurant basic info validation
        if (request.getRestaurantName() == null || request.getRestaurantName().trim().isEmpty()) {
            errors.add("Tên nhà hàng không được để trống");
        } else if (request.getRestaurantName().length() > 255) {
            errors.add("Tên nhà hàng không được vượt quá 255 ký tự");
        }
        
        if (request.getRestaurantAddress() == null || request.getRestaurantAddress().trim().isEmpty()) {
            errors.add("Địa chỉ nhà hàng không được để trống");
        } else if (request.getRestaurantAddress().length() > 500) {
            errors.add("Địa chỉ nhà hàng không được vượt quá 500 ký tự");
        }
        
        // Delivery info validation
        if (request.getDeliveryAddress() == null || request.getDeliveryAddress().trim().isEmpty()) {
            errors.add("Địa chỉ giao hàng không được để trống");
        } else if (request.getDeliveryAddress().length() > 500) {
            errors.add("Địa chỉ giao hàng không được vượt quá 500 ký tự");
        }
        
        // Customer info validation
        if (request.getCustomerName() == null || request.getCustomerName().trim().isEmpty()) {
            errors.add("Tên khách hàng không được để trống");
        } else if (request.getCustomerName().length() > 100) {
            errors.add("Tên khách hàng không được vượt quá 100 ký tự");
        }
        
        if (request.getCustomerPhone() == null || request.getCustomerPhone().trim().isEmpty()) {
            errors.add("Số điện thoại khách hàng không được để trống");
        }
        
        // Payment method validation
        if (request.getPaymentMethod() == null || request.getPaymentMethod().trim().isEmpty()) {
            errors.add("Phương thức thanh toán không được để trống");
        } else if (!request.getPaymentMethod().matches("^(COD|ONLINE)$")) {
            errors.add("Phương thức thanh toán phải là COD hoặc ONLINE");
        }
        
        // Notes validation (optional but with size limit)
        if (request.getNotes() != null && request.getNotes().length() > 1000) {
            errors.add("Ghi chú không được vượt quá 1000 ký tự");
        }
    }
    
    /**
     * Validate business rules
     */
    private void validateBusinessRules(CreateOrderRequest request, List<String> errors) {
        // Minimum order validation
        if (request.getItems() == null || request.getItems().isEmpty()) {
            errors.add("Đơn hàng phải có ít nhất một sản phẩm");
            return;
        }
        
        // Maximum items per order
        if (request.getItems().size() > 50) {
            errors.add("Đơn hàng không được vượt quá 50 sản phẩm");
        }
        
        // Validate each item
        for (int i = 0; i < request.getItems().size(); i++) {
            CreateOrderRequest.OrderItemRequest item = request.getItems().get(i);
            validateOrderItem(item, i + 1, errors);
        }
        
        // Phone number format validation (more strict)
        if (request.getCustomerPhone() != null && !isValidVietnamesePhoneNumber(request.getCustomerPhone())) {
            errors.add("Số điện thoại khách hàng không đúng định dạng Việt Nam");
        }
        
        if (request.getRestaurantPhone() != null && !isValidVietnamesePhoneNumber(request.getRestaurantPhone())) {
            errors.add("Số điện thoại nhà hàng không đúng định dạng Việt Nam");
        }
    }
    
    /**
     * Validate individual order item
     */
    private void validateOrderItem(CreateOrderRequest.OrderItemRequest item, int itemIndex, List<String> errors) {
        String prefix = "Sản phẩm " + itemIndex + ": ";
        
        // Menu Item ID validation
        if (item.getMenuItemId() == null) {
            errors.add(prefix + "Menu Item ID không được để trống");
        } else if (item.getMenuItemId() <= 0) {
            errors.add(prefix + "Menu Item ID phải là số dương");
        }
        
        // Menu Item Name validation
        if (item.getMenuItemName() == null || item.getMenuItemName().trim().isEmpty()) {
            errors.add(prefix + "Tên sản phẩm không được để trống");
        } else if (item.getMenuItemName().length() > 255) {
            errors.add(prefix + "Tên sản phẩm không được vượt quá 255 ký tự");
        }
        
        // Quantity validation
        if (item.getQuantity() == null) {
            errors.add(prefix + "Số lượng không được để trống");
        } else if (item.getQuantity() <= 0) {
            errors.add(prefix + "Số lượng phải lớn hơn 0");
        } else if (item.getQuantity() > 99) {
            errors.add(prefix + "Số lượng không được vượt quá 99");
        }
        
        // Price validation
        if (item.getPrice() == null) {
            errors.add(prefix + "Giá sản phẩm không được để trống");
        } else if (item.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(prefix + "Giá sản phẩm phải lớn hơn 0");
        } else if (item.getPrice().compareTo(new BigDecimal("10000000")) > 0) {
            errors.add(prefix + "Giá sản phẩm không được vượt quá 10,000,000 VND");
        }
        
        // Notes validation (optional but with size limit)
        if (item.getNotes() != null && item.getNotes().length() > 500) {
            errors.add(prefix + "Ghi chú sản phẩm không được vượt quá 500 ký tự");
        }
        
        // Calculate total price for this item (only if both price and quantity are valid)
        if (item.getPrice() != null && item.getQuantity() != null && 
            item.getPrice().compareTo(BigDecimal.ZERO) > 0 && item.getQuantity() > 0) {
            
            BigDecimal itemTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            if (itemTotal.compareTo(new BigDecimal("50000000")) > 0) {
                errors.add(prefix + "Tổng giá trị sản phẩm không được vượt quá 50,000,000 VND");
            }
        }
    }
    
    /**
     * Validate coordinates for Vietnam region
     */
    private void validateCoordinates(CreateOrderRequest request, List<String> errors) {
        // Vietnam coordinate bounds
        double MIN_LAT = 8.0, MAX_LAT = 24.0;
        double MIN_LNG = 102.0, MAX_LNG = 110.0;
        
        // Check required delivery coordinates
        if (request.getDeliveryLat() == null || request.getDeliveryLng() == null) {
            errors.add("Tọa độ giao hàng (latitude và longitude) là bắt buộc");
        } else {
            // Validate delivery coordinates bounds
            if (request.getDeliveryLat() < MIN_LAT || request.getDeliveryLat() > MAX_LAT) {
                errors.add("Tọa độ giao hàng (latitude) phải trong phạm vi Việt Nam (8.0 - 24.0)");
            }
            if (request.getDeliveryLng() < MIN_LNG || request.getDeliveryLng() > MAX_LNG) {
                errors.add("Tọa độ giao hàng (longitude) phải trong phạm vi Việt Nam (102.0 - 110.0)");
            }
        }
        
        // Check required pickup coordinates  
        if (request.getPickupLat() == null || request.getPickupLng() == null) {
            errors.add("Tọa độ pickup (latitude và longitude) là bắt buộc");
        } else {
            // Validate pickup coordinates bounds
            if (request.getPickupLat() < MIN_LAT || request.getPickupLat() > MAX_LAT) {
                errors.add("Tọa độ pickup (latitude) phải trong phạm vi Việt Nam (8.0 - 24.0)");
            }
            if (request.getPickupLng() < MIN_LNG || request.getPickupLng() > MAX_LNG) {
                errors.add("Tọa độ pickup (longitude) phải trong phạm vi Việt Nam (102.0 - 110.0)");
            }
        }
        
        // Validate distance between pickup and delivery (only if both coordinates are valid)
        if (request.getPickupLat() != null && request.getPickupLng() != null &&
            request.getDeliveryLat() != null && request.getDeliveryLng() != null) {
            
            double distance = calculateDistance(
                request.getPickupLat(), request.getPickupLng(),
                request.getDeliveryLat(), request.getDeliveryLng()
            );
            
            if (distance > 100.0) { // 100km max distance
                errors.add("Khoảng cách giữa điểm lấy hàng và giao hàng không được vượt quá 100km");
            }
            
            if (distance < 0.1) { // Minimum 100m distance to avoid same location
                errors.add("Khoảng cách giữa điểm lấy hàng và giao hàng phải ít nhất 100 mét");
            }
        }
    }
    
    /**
     * Validate financial data consistency
     */
    private void validateFinancialData(CreateOrderRequest request, List<String> errors) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return;
        }
        
        // Calculate total order value
        BigDecimal totalValue = request.getItems().stream()
            .filter(item -> item.getPrice() != null && item.getQuantity() != null)
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Minimum order value
        if (totalValue.compareTo(new BigDecimal("10000")) < 0) {
            errors.add("Giá trị đơn hàng tối thiểu là 10,000 VND");
        }
        
        // Maximum order value
        if (totalValue.compareTo(new BigDecimal("100000000")) > 0) {
            errors.add("Giá trị đơn hàng không được vượt quá 100,000,000 VND");
        }
    }
    
    /**
     * Validate user context and permissions
     */
    private void validateUserContext(CreateOrderRequest request, Long userId, List<String> errors) {
        if (userId == null || userId <= 0) {
            errors.add("User ID không hợp lệ");
        }
        
        // Additional business rules can be added here
        // E.g., user credit limit, delivery zone restrictions, etc.
    }
    
    /**
     * Validate Vietnamese phone number format
     */
    private boolean isValidVietnamesePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        
        // Vietnamese phone number patterns
        String cleanPhone = phoneNumber.replaceAll("\\s+", "");
        return cleanPhone.matches("^(\\+84|84|0)(3|5|7|8|9)[0-9]{8}$");
    }
    
    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371; // Radius of the earth in km
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c;
        
        return distance;
    }
}
