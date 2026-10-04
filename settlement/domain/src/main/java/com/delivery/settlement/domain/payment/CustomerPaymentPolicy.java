package com.delivery.settlement.domain.payment;

/** Customer payment stays unavailable until order and payer ownership exist together. */
public final class CustomerPaymentPolicy {
    private CustomerPaymentPolicy() {}

    public record Identity(boolean customer, Long principalId, Long legacyUserId) {}

    public static void create(Identity identity, Long orderId) {
        requireIdentity(identity);
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("orderId must be positive");
        throw new Unsupported("CUSTOMER_ORDER_PAYMENT_UNSUPPORTED");
    }

    public static void byReference(Identity identity, String paymentRef) {
        requireIdentity(identity);
        if (paymentRef == null || !paymentRef.matches("PAY-[A-Za-z0-9-]{1,59}")) {
            throw new IllegalArgumentException("paymentRef is invalid");
        }
        throw new Unsupported("CUSTOMER_PAYMENT_OWNERSHIP_UNSUPPORTED");
    }

    private static void requireIdentity(Identity identity) {
        if (identity == null || !identity.customer()) throw new AccessDenied("Only USER can access this endpoint");
        if (identity.principalId() == null || identity.principalId() <= 0
                || identity.legacyUserId() == null || identity.legacyUserId() <= 0) {
            throw new AccessDenied("Authenticated user identity is required");
        }
    }

    public static final class AccessDenied extends RuntimeException {
        public AccessDenied(String message) { super(message); }
    }

    public static final class Unsupported extends RuntimeException {
        public Unsupported(String message) { super(message); }
    }
}
