package com.delivery.settlement.domain.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Payment state independent of persistence. Legacy defaults and terminal replay are preserved. */
public final class WorkflowPayment {
    private Long id;
    private String paymentRef;
    private Long entityId;
    private String entityType;
    private Long orderId;
    private String provider;
    private BigDecimal amount;
    private String currency;
    private String purpose;
    private String status;
    private String paymentUrl;
    private String providerTransactionId;
    private String callbackPayload;
    private Long settlementTransactionId;
    private String returnUrl;
    private String ipAddress;
    private LocalDateTime createdAt;
    private LocalDateTime expiredAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getPaymentRef() { return paymentRef; }
    public void setPaymentRef(String value) { paymentRef = value; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long value) { entityId = value; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String value) { entityType = value; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long value) { orderId = value; }
    public String getProvider() { return provider; }
    public void setProvider(String value) { provider = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String value) { purpose = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String value) { paymentUrl = value; }
    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String value) { providerTransactionId = value; }
    public String getCallbackPayload() { return callbackPayload; }
    public void setCallbackPayload(String value) { callbackPayload = value; }
    public Long getSettlementTransactionId() { return settlementTransactionId; }
    public void setSettlementTransactionId(Long value) { settlementTransactionId = value; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String value) { returnUrl = value; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String value) { ipAddress = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(LocalDateTime value) { expiredAt = value; }
    public boolean isPending() { return "PENDING".equals(status); }
    public boolean isTopUp() { return "DEPOSIT_TOPUP".equals(purpose); }
    public boolean matchesAmount(Long received) {
        return received == null || received == amount.longValue() * 100;
    }
    public void requireFake() {
        if (!"FAKE".equalsIgnoreCase(provider)) {
            throw new IllegalArgumentException("Only FAKE payments can be confirmed via this endpoint");
        }
    }
    public static String entityType(String value) {
        if (value == null) return "SHIPPER";
        return switch (value.toUpperCase()) {
            case "RESTAURANT", "SHIPPER", "SYSTEM" -> value.toUpperCase();
            default -> "SHIPPER";
        };
    }
    public static String purpose(String value) {
        if (value == null) return "DEPOSIT_TOPUP";
        return switch (value.toUpperCase()) {
            case "DEPOSIT_TOPUP", "ORDER_PAYMENT", "WITHDRAWAL" -> value.toUpperCase();
            default -> "DEPOSIT_TOPUP";
        };
    }
}
