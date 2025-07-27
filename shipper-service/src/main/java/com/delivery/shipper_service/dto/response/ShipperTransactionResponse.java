package com.delivery.shipper_service.dto.response;

import com.delivery.shipper_service.entity.ShipperTransaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ShipperTransactionResponse {
    private Long id;
    private Long shipperId;
    private Long relatedOrderId;
    private ShipperTransaction.TransactionType transactionType;
    private BigDecimal amount;
    private String description;
    private LocalDateTime createdAt;

    // Constructors
    public ShipperTransactionResponse() {}

    public ShipperTransactionResponse(Long id, Long shipperId, Long relatedOrderId,
                                    ShipperTransaction.TransactionType transactionType,
                                    BigDecimal amount, String description, LocalDateTime createdAt) {
        this.id = id;
        this.shipperId = shipperId;
        this.relatedOrderId = relatedOrderId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.description = description;
        this.createdAt = createdAt;
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

    public Long getRelatedOrderId() {
        return relatedOrderId;
    }

    public void setRelatedOrderId(Long relatedOrderId) {
        this.relatedOrderId = relatedOrderId;
    }

    public ShipperTransaction.TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(ShipperTransaction.TransactionType transactionType) {
        this.transactionType = transactionType;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
