package com.delivery.shipper_service.dto.request;

import java.math.BigDecimal;

public class BalanceTransactionRequest {
    private BigDecimal amount;
    private String description;

    // Constructors
    public BalanceTransactionRequest() {}

    public BalanceTransactionRequest(BigDecimal amount, String description) {
        this.amount = amount;
        this.description = description;
    }

    // Getters and Setters
    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
