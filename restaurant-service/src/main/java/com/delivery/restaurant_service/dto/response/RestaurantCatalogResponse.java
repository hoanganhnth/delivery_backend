package com.delivery.restaurant_service.dto.response;

import lombok.*;

import java.time.LocalTime;
import java.util.List;

/**
 * Lightweight response cho Restaurant Catalog - tối ưu cho Home Feed
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantCatalogResponse {
    
    // Basic restaurant info
    private Long id;
    private String name;
    private String address;
    private String phone;
    
    // Location data
    private Double latitude;
    private Double longitude;
    
    // Operating info
    private LocalTime openingHour;
    private LocalTime closingHour;
    private Boolean isOpen;
    private Boolean isAvailable;
    
    // Display info
    private String image;
    private String cuisine; // Loại ẩm thực
    private Double rating;
    private Integer reviewCount;
    
    // Pricing & delivery
    private Double avgPrice;
    private Integer deliveryTime; // phút
    private Double deliveryFee;
    
    // Featured items cho home feed (chỉ 2-3 món)
    private List<FeaturedMenuItem> featuredItems;
    
    // Metrics
    private Integer totalMenuItems;
    private Double popularityScore; // Cho sorting
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FeaturedMenuItem {
        private Long id;
        private String name;
        private Double price;
        private String imageUrl;
        private Boolean isAvailable;
    }
}
