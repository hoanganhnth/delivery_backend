package com.delivery.order_service.dto.request;

import lombok.Data;

@Data
public class CancelOrderRequest {
    private String reason;
}
