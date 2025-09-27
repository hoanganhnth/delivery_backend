package com.delivery.restaurant_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO cho MenuItem Catalog - Full menu item data
 * Dành cho Restaurant Detail Page
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemCatalogResponse {
    
    // Core MenuItem Info
    private Long menuItemId;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    
    // Availability & Status
    private Boolean isAvailable;
    private Boolean isPopular;
    private Boolean isRecommended;
    private Boolean isSpicy;
    private Boolean isVegetarian;
    
    // Media & Visuals
    private String imageUrl;
    private List<String> additionalImages;
    
    // Metrics & Social Proof
    private Integer orderCount; // Số lần được order
    private Double rating; // Rating trung bình từ reviews
    private Integer reviewCount; // Số lượng reviews
    
    // Nutritional Info (optional)
    private Integer calories;
    private String allergens; // e.g., "nuts, dairy, gluten"
    private String ingredients; // Brief ingredient list
    
    // Time & Preparation
    private Integer preparationTimeMinutes;
    private LocalDateTime lastUpdated;
    
    // Restaurant Reference
    private Long restaurantId;
    private String restaurantName;
    
    // Promotion & Pricing
    private BigDecimal originalPrice; // Nếu có discount
    private Integer discountPercentage; // % discount nếu có
    private LocalDateTime promotionEndTime; // Thời hạn promotion
    
    // Customization Options
    private Boolean hasCustomizations; // Có thể customize không
    private List<CustomizationOptionResponse> customizationOptions;
    
    // Inner class for customization options
    @Data
    @NoArgsConstructor  
    @AllArgsConstructor
    public static class CustomizationOptionResponse {
        private String optionName; // e.g., "Size", "Spice Level"
        private List<String> choices; // e.g., ["Small", "Medium", "Large"]
        private BigDecimal additionalPrice; // Giá thêm nếu có
        private Boolean isRequired; // Bắt buộc chọn hay không
    }
}
