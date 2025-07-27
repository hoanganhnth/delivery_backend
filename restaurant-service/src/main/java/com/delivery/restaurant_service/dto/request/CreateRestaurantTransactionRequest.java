package com.delivery.restaurant_service.dto.request;

import com.delivery.restaurant_service.entity.RestaurantTransaction;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateRestaurantTransactionRequest {
    private Long id;
    private Long restaurantId;
    private Long orderId;
    private RestaurantTransaction.TypeTransaction type;
    private BigDecimal amount;
    private String description;
}
