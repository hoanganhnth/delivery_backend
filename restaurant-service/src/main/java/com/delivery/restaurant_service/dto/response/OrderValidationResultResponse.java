package com.delivery.restaurant_service.dto.response;

import lombok.*;

import java.util.List;

/**
 * Response DTO cho order validation
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderValidationResultResponse {
    
    private Boolean isValid;
    private String message;
    private Double calculatedTotal;
    private List<ValidationError> errors;
    private RestaurantInfo restaurantInfo;
    private List<ItemValidationInfo> itemValidations;
    
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
    public static class RestaurantInfo {
        private Long restaurantId;
        private Long creatorId;
        private String restaurantName;
        private String restaurantAddress;
        private String restaurantPhone;
        private Double latitude;
        private Double longitude;
        private Boolean isAvailable;
        private Boolean isOpen;
        private String operatingHours;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemValidationInfo {
        private Long menuItemId;
        private String menuItemName;
        private Boolean isAvailable;
        private Double actualPrice;
        private Double expectedPrice;
        private Boolean priceMatches;
        private Integer requestedQuantity;
        private Integer availableStock;
        private Boolean hasEnoughStock;
    }
}
