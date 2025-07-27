package com.delivery.shipper_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ShipperBalanceResponse {
    private Long id;
    private Long shipperId;
    private BigDecimal balance;
    private BigDecimal holdingBalance;
    private BigDecimal totalBalance;
    private LocalDateTime updatedAt;

    // Constructors
    public ShipperBalanceResponse() {}

    public ShipperBalanceResponse(Long id, Long shipperId, BigDecimal balance, 
                                BigDecimal holdingBalance, LocalDateTime updatedAt) {
        this.id = id;
        this.shipperId = shipperId;
        this.balance = balance;
        this.holdingBalance = holdingBalance;
        this.totalBalance = balance.add(holdingBalance);
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShipperId() {
        return shipperId;
    }

    public void setShipperId(Long shipperId) {
        this.shipperId = shipperId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
        updateTotalBalance();
    }

    public BigDecimal getHoldingBalance() {
        return holdingBalance;
    }

    public void setHoldingBalance(BigDecimal holdingBalance) {
        this.holdingBalance = holdingBalance;
        updateTotalBalance();
    }

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(BigDecimal totalBalance) {
        this.totalBalance = totalBalance;
    }

    private void updateTotalBalance() {
        if (balance != null && holdingBalance != null) {
            this.totalBalance = balance.add(holdingBalance);
        }
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
