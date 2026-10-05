package com.delivery.promotion.domain;

/** Immutable lifecycle outcomes. Caller applies audit fields under its existing lock. */
public final class VoucherLifecyclePolicy {
    private VoucherLifecyclePolicy() {}

    public record Outcome(boolean changed, String approvalStatus, Boolean active, String reason,
                          String failure, boolean conflict) {}

    public static Outcome approve(Voucher voucher) {
        Outcome failure = pendingFailure(voucher);
        return failure != null ? failure : new Outcome(true, "APPROVED", true, null, null, false);
    }

    public static Outcome reject(Voucher voucher, String reason) {
        Outcome failure = pendingFailure(voucher);
        return failure != null ? failure
                : new Outcome(true, "REJECTED", false, normalizedReason(reason, "Rejected by admin"), null, false);
    }

    /** Ownership-verified creation rail only; does not reinterpret historical pending/rejected rows. */
    public static Outcome approveNewOwnedShop() {
        return new Outcome(true, "APPROVED", true, null, null, false);
    }

    public static Outcome setActive(Voucher voucher, boolean active) {
        if (active) {
            if (voucher.getDeletedAt() != null) return deletedFailure();
            if (!WalletVoucherPolicy.isApproved(voucher)) return failure("Voucher is not approved", true);
        }
        return new Outcome(true, null, active, null, null, false);
    }

    public static Outcome retire(Voucher voucher, String reason) {
        if (voucher.getDeletedAt() != null) return new Outcome(false, null, null, null, null, false);
        return new Outcome(true, null, false, normalizedReason(reason, "deleted_by_request"), null, false);
    }

    private static Outcome pendingFailure(Voucher voucher) {
        if (voucher.getDeletedAt() != null) return deletedFailure();
        if (voucher.getCreatorType() != Voucher.CreatorType.SHOP) {
            return failure("Only shop vouchers require approval", false);
        }
        if (!"PENDING".equalsIgnoreCase(voucher.getApprovalStatus())) {
            return failure("Voucher is not pending approval", true);
        }
        return null;
    }

    private static Outcome deletedFailure() {
        return failure("Deleted voucher cannot be activated", true);
    }

    private static Outcome failure(String message, boolean conflict) {
        return new Outcome(false, null, null, null, message, conflict);
    }

    private static String normalizedReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason.trim();
    }
}
