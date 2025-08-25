package com.delivery.delivery_service.service;

import com.delivery.delivery_service.dto.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * ✅ Event Validation Service theo Backend Instructions
 * Validate incoming events để đảm bảo data integrity
 */
@Slf4j
@Service
public class EventValidationService {
    
    /**
     * Validate OrderCreatedEvent và return validation results
     */
    public ValidationResult validateOrderCreatedEvent(OrderCreatedEvent event) {
        if (event == null) {
            return ValidationResult.invalid("OrderCreatedEvent không được null");
        }
        
        List<String> errors = new ArrayList<>();
        
        // Validate required fields
        if (event.getOrderId() == null || event.getOrderId() <= 0) {
            errors.add("Order ID không được null hoặc <= 0");
        }
        
        if (event.getUserId() == null || event.getUserId() <= 0) {
            errors.add("User ID không được null hoặc <= 0");
        }
        
        if (event.getRestaurantId() == null || event.getRestaurantId() <= 0) {
            errors.add("Restaurant ID không được null hoặc <= 0");
        }
        
        if (event.getStatus() == null || event.getStatus().trim().isEmpty()) {
            errors.add("Status không được null hoặc rỗng");
        }
        
        // Validate financial fields
        if (event.getSubtotalPrice() == null || event.getSubtotalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Subtotal price phải lớn hơn 0");
        }
        
        if (event.getTotalPrice() == null || event.getTotalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Total price phải lớn hơn 0");
        }
        
        if (event.getPaymentMethod() == null || 
            (!event.getPaymentMethod().equals("COD") && !event.getPaymentMethod().equals("ONLINE"))) {
            errors.add("Payment method chỉ có thể là COD hoặc ONLINE");
        }
        
        // Validate address fields
        if (event.getDeliveryAddress() == null || event.getDeliveryAddress().trim().length() < 10) {
            errors.add("Delivery address phải có ít nhất 10 ký tự");
        }
        
        // if (event.getRestaurantName() == null || event.getRestaurantName().trim().length() < 2) {
        //     errors.add("Restaurant name phải có ít nhất 2 ký tự");
        // }
        
        if (event.getRestaurantAddress() == null || event.getRestaurantAddress().trim().length() < 10) {
            errors.add("Restaurant address phải có ít nhất 10 ký tự");
        }
        
        // Validate customer info
        // if (event.getCustomerName() == null || event.getCustomerName().trim().length() < 2) {
        //     errors.add("Customer name phải có ít nhất 2 ký tự");
        // }
        
        // if (event.getCustomerPhone() == null || !event.getCustomerPhone().matches("^[0-9]{10,11}$")) {
        //     errors.add("Customer phone phải là 10-11 chữ số");
        // }
        
        // Validate coordinates if present
        if (event.getDeliveryLat() != null && (event.getDeliveryLat() < -90 || event.getDeliveryLat() > 90)) {
            errors.add("Delivery latitude phải trong khoảng -90 đến 90");
        }
        
        if (event.getDeliveryLng() != null && (event.getDeliveryLng() < -180 || event.getDeliveryLng() > 180)) {
            errors.add("Delivery longitude phải trong khoảng -180 đến 180");
        }
        
        if (event.getPickupLat() != null && (event.getPickupLat() < -90 || event.getPickupLat() > 90)) {
            errors.add("Pickup latitude phải trong khoảng -90 đến 90");
        }
        
        if (event.getPickupLng() != null && (event.getPickupLng() < -180 || event.getPickupLng() > 180)) {
            errors.add("Pickup longitude phải trong khoảng -180 đến 180");
        }
        
        // if (event.getCreatedAt() == null) {
        //     errors.add("Created at không được null");
        // }
        
        if (errors.isEmpty()) {
            log.debug("✅ OrderCreatedEvent validation passed for order: {}", event.getOrderId());
            return ValidationResult.valid();
        }
        
        String errorMessage = String.join("; ", errors);
        log.warn("⚠️ OrderCreatedEvent validation failed for order: {} - Errors: {}", 
                event.getOrderId(), errorMessage);
        
        return ValidationResult.invalid(errorMessage);
    }
    
    /**
     * Validate critical fields only (for fallback processing)
     */
    public boolean hasMinimumRequiredFields(OrderCreatedEvent event) {
        if (event == null) return false;
        
        return event.getOrderId() != null 
            && event.getUserId() != null 
            && event.getRestaurantId() != null
            && event.getDeliveryAddress() != null
            && event.getCustomerName() != null
            && event.getCustomerPhone() != null;
    }
    
    /**
     * Validation result wrapper
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
