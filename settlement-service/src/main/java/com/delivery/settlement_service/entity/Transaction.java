package com.delivery.settlement_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction entity - Source of Truth
 * IMMUTABLE: Never UPDATE or DELETE transactions
 * Only append new transactions
 * To reverse: create new transaction with opposite direction
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_entity", columnList = "entity_id, entity_type"),
    @Index(name = "idx_transactions_order", columnList = "order_id"),
    @Index(name = "idx_transactions_status", columnList = "status"),
    @Index(name = "idx_transactions_reason", columnList = "reason"),
    @Index(name = "idx_transactions_created_at", columnList = "created_at"),
    @Index(name = "idx_transactions_entity_status", columnList = "entity_id, entity_type, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private EntityType entityType;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private TransactionDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private TransactionReason reason;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.COMPLETED;

    /**
     * Giao dịch thuộc ví nào (EARNINGS hoặc DEPOSIT)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", nullable = false, length = 20)
    @Builder.Default
    private WalletType walletType = WalletType.EARNINGS;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Transaction direction: CREDIT (money in) or DEBIT (money out)
     */
    public enum TransactionDirection {
        CREDIT,  // Money coming IN (increases balance)
        DEBIT    // Money going OUT (decreases balance)
    }

    /**
     * Ví nào xử lý giao dịch này
     */
    public enum WalletType {
        EARNINGS,   // Ví Thu nhập (tiền công, thưởng, rút tiền)
        DEPOSIT     // Ví Ký quỹ (nạp trước, trừ COD, trừ chiết khấu)
    }

    /**
     * Business reason for the transaction
     */
    public enum TransactionReason {
        // Income/Credits
        ORDER_EARNING,          // Restaurant/Shipper earned from order
        DELIVERY_FEE,           // Shipper delivery fee (→ Ví Thu nhập)
        DEPOSIT,                // Manual deposit (legacy)
        DEPOSIT_TOPUP,          // Shipper nạp tiền vào Ví Ký quỹ
        REFUND_RECEIVED,        // Refund received
        ADJUSTMENT_CREDIT,      // Admin adjustment (credit)
        RELEASE,                // Release from holding (shipper)
        COD_REFUND,             // Refund COD deduction (order cancelled after pickup)

        // Expenses/Debits
        PLATFORM_COMMISSION,    // Platform commission deduction
        WITHDRAW,               // Withdrawal request (→ Ví Thu nhập)
        REFUND_ISSUED,          // Refund issued to customer
        PENALTY,                // Penalty/fine
        ADJUSTMENT_DEBIT,       // Admin adjustment (debit)
        HOLD,                   // Move to holding balance (shipper)
        COD_DEDUCTION,          // COD collection deduction (legacy)
        COD_SETTLEMENT          // Đối trừ tiền COD thu hộ (→ Ví Ký quỹ)
    }

    /**
     * Transaction status
     */
    public enum TransactionStatus {
        PENDING,    // Awaiting approval (withdrawals, manual adjustments)
        COMPLETED,  // Transaction completed successfully
        FAILED,     // Transaction failed
        REVERSED    // Transaction was reversed (create opposite transaction)
    }
}
