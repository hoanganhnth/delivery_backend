package com.delivery.shipper_service.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipper_balances")
public class ShipperBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipper_id", nullable = false, unique = true)
    private Long shipperId;

    @Column(name = "balance", precision = 12, scale = 2, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "holding_balance", precision = 12, scale = 2, nullable = false)
    private BigDecimal holdingBalance = BigDecimal.ZERO;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public ShipperBalance() {}

    public ShipperBalance(Long shipperId) {
        this.shipperId = shipperId;
        this.balance = BigDecimal.ZERO;
        this.holdingBalance = BigDecimal.ZERO;
    }

    public ShipperBalance(Long shipperId, BigDecimal balance, BigDecimal holdingBalance) {
        this.shipperId = shipperId;
        this.balance = balance;
        this.holdingBalance = holdingBalance;
    }

    // Business methods
    public BigDecimal getTotalBalance() {
        return balance.add(holdingBalance);
    }

    public void addBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void deductBalance(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void addHoldingBalance(BigDecimal amount) {
        this.holdingBalance = this.holdingBalance.add(amount);
    }

    public void deductHoldingBalance(BigDecimal amount) {
        this.holdingBalance = this.holdingBalance.subtract(amount);
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
    }

    public BigDecimal getHoldingBalance() {
        return holdingBalance;
    }

    public void setHoldingBalance(BigDecimal holdingBalance) {
        this.holdingBalance = holdingBalance;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
