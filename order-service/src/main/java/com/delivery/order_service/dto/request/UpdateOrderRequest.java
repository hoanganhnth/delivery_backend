package com.delivery.order_service.dto.request;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UpdateOrderRequest {

    private String status;
    private Long shipperId;
    private String notes;
    private BigDecimal shippingFee;

}
