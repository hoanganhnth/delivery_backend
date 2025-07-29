package com.delivery.delivery_service.dto.request;

import com.delivery.delivery_service.entity.DeliveryStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateLocationRequest {

    private Double lat;
    private Double lng;
    private DeliveryStatus status; // Optional: để update status cùng lúc

}
