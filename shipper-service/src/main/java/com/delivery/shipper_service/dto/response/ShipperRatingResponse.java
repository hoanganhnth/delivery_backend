package com.delivery.shipper_service.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShipperRatingResponse {
    private Long id;
    private Long shipperId;
    private Long customerId;
    private Long orderId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
