package com.delivery.restaurant_service.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RestaurantRatingResponse {
    private Long id;
    private Long restaurantId;
    private Long customerId;
    private Long orderId;
    private Integer rating;
    private String comment;
    private String status;
    private LocalDateTime createdAt;
}
