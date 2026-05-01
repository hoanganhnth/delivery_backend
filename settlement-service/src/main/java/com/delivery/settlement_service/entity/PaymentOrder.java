package com.delivery.settlement_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PaymentOrder — Lưu trữ mỗi giao dịch thanh toán qua cổng bên thứ ba.
 * Trạng thái: PENDING → SUCCESS / FAILED / EXPIRED
 */
@Entity
@Table(name = "payment_orders", indexes = {
    @Index(name = "idx_payment_ref", columnList = "payment_ref", unique = true),
    @Index(name = "idx_payment_entity", columnList = "entity_id, entity_type"),
    @Index(name = "idx_payment_status", columnList = "status"),
    @Index(name = "idx_payment_provider", columnList = "provider")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mã tham chiếu duy nhất cho giao dịch (UUID-based hoặc timestamp-based)
     */
    @Column(name = "payment_ref", nullable = false, unique = true, length = 64)
    private String paymentRef;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private EntityType entityType;

    @Column(name = "order_id")
    private Long orderId;

    /**
     * Nhà cung cấp thanh toán: VNPAY, MOMO, ZALOPAY, FAKE...
     */
    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    /**
     * Mục đích: DEPOSIT_TOPUP, ORDER_PAYMENT...
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private PaymentPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    /**
     * Mã giao dịch từ phía cổng thanh toán (VNPay txnRef, Momo transId...)
     */
    @Column(name = "provider_transaction_id", length = 128)
    private String providerTransactionId;

    /**
     * URL để redirect người dùng đến cổng thanh toán
     */
    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    /**
     * Raw JSON payload từ callback của cổng thanh toán
     */
    @Column(name = "callback_payload", columnDefinition = "TEXT")
    private String callbackPayload;

    /**
     * ID của Transaction trong bảng transactions (sau khi thanh toán thành công)
     */
    @Column(name = "settlement_transaction_id")
    private Long settlementTransactionId;

    @Column(name = "return_url", columnDefinition = "TEXT")
    private String returnUrl;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ═══════════════════════════════
    // Enums
    // ═══════════════════════════════

    public enum PaymentPurpose {
        DEPOSIT_TOPUP,      // Shipper nạp tiền ký quỹ
        ORDER_PAYMENT,      // Khách hàng thanh toán đơn hàng (future)
        WITHDRAWAL          // Rút tiền (future)
    }

    public enum PaymentStatus {
        PENDING,    // Đang chờ thanh toán
        SUCCESS,    // Thanh toán thành công
        FAILED,     // Thanh toán thất bại
        EXPIRED     // Hết hạn thanh toán
    }
}
