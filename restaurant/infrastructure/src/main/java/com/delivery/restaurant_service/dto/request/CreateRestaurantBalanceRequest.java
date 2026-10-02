package com.delivery.restaurant_service.dto.request;


import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRestaurantBalanceRequest {

    private Long restaurantId;

    private BigDecimal availableBalance;

    private BigDecimal pendingBalance;

    private BigDecimal totalEarnings;
}
