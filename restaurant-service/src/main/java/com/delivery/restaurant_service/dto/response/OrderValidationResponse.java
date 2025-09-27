package com.delivery.restaurant_service.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderValidationResponse {
    private Boolean isValid;
    private String message;
    private Double calculatedTotal;
    private List<ValidationError> errors;
    private RestaurantValidationInfo restaurantInfo;
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidationError {
        private String field;
        private String errorCode;
        private String message;
        private Object invalidValue;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RestaurantValidationInfo {
        private Long restaurantId;
        private String restaurantName;
        private Boolean isOpen;
        private String operatingHours;
        private Boolean isAvailable;
    }
}
