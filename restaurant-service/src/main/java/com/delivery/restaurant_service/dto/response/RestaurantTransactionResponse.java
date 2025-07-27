package com.delivery.restaurant_service.dto.response;

import com.delivery.restaurant_service.entity.RestaurantTransaction;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class RestaurantTransactionResponse {
    private Long id;
    private Long restaurantId;
    private Long orderId;
    private RestaurantTransaction.TypeTransaction type;
    private BigDecimal amount;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
