package com.delivery.restaurant_service.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Getter
@Setter
public class RestaurantBalanceResponse {
    private Long id;
    private Long restaurantId;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal totalEarnings;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
