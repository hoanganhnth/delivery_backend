package com.delivery.shipper_service.dto.request;

import lombok.Data;

@Data
public class ShipperRatingRequest {
    private Long orderId;
    private Integer rating;
    private String comment;
}
