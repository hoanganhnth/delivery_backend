package com.delivery.restaurant_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class WithdrawalRequest {

    private BigDecimal amount;

    private String bankAccount; // Optional for future use

    private String notes; // Optional
}
