package com.delivery.delivery_service.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignDeliveryRequest {

    private Long orderId;
    private Long shipperId;
    private String pickupAddress;
    private Double pickupLat;
    private Double pickupLng;
    private String deliveryAddress;
    private Double deliveryLat;
    private Double deliveryLng;
    private String notes;

}
