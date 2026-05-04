package com.delivery.restaurant_service.dto.request;

import lombok.Data;

@Data
public class RestaurantRatingRequest {
    private Long orderId;
    private Integer rating;
    private String comment;
}
