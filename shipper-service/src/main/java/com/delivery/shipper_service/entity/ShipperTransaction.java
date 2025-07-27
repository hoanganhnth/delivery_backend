package com.delivery.shipper_service.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipper_transactions")
public class ShipperTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipper_id", nullable = false)
    private Long shipperId;

    @Column(name = "related_order_id")
    private Long relatedOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Enum for transaction types
    public enum TransactionType {
        DEPOSIT,    // Nạp tiền
        WITHDRAW,   // Rút tiền
        EARN,       // Kiếm được từ đơn hàng
        PENALTY,    // Phạt
        HOLD,       // Giữ tạm
        RELEASE     // Giải phóng tiền giữ tạm
    }

    // Constructors
    public ShipperTransaction() {}

    public ShipperTransaction(Long shipperId, TransactionType transactionType, 
                            BigDecimal amount, String description) {
        this.shipperId = shipperId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.description = description;
    }

    public ShipperTransaction(Long shipperId, Long relatedOrderId, 
                            TransactionType transactionType, BigDecimal amount, 
                            String description) {
        this.shipperId = shipperId;
        this.relatedOrderId = relatedOrderId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.description = description;
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

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
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
