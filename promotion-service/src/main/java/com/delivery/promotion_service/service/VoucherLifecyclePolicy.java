package com.delivery.promotion_service.service;

import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.exception.PromotionConflictException;
import java.time.LocalDateTime;

/** Approval, activation and retirement transitions; caller owns locks and transaction. */
final class VoucherLifecyclePolicy {
    private VoucherLifecyclePolicy() {}

    static void approve(Voucher voucher, Long actor, LocalDateTime now) {
        requirePendingShop(voucher);
        voucher.setApprovalStatus("APPROVED");
        voucher.setApprovedByPrincipalId(actor);
        voucher.setApprovedAt(now);
        voucher.setRejectionReason(null);
        voucher.setActive(true);
    }

    static void reject(Voucher voucher, Long actor, String reason, LocalDateTime now) {
        requirePendingShop(voucher);
        voucher.setApprovalStatus("REJECTED");
        voucher.setApprovedByPrincipalId(actor);
        voucher.setApprovedAt(now);
        voucher.setRejectionReason(normalizedReason(reason, "Rejected by admin"));
        voucher.setActive(false);
    }

    static void setActive(Voucher voucher, boolean active) {
        if (active) {
            requireNotDeleted(voucher);
            if (!WalletVoucherPolicy.isApproved(voucher)) {
                throw new PromotionConflictException("Voucher is not approved");
            }
        }
        voucher.setActive(active);
    }

    static boolean retire(Voucher voucher, Long actor, String reason, LocalDateTime now) {
        if (voucher.getDeletedAt() != null) return false;
        voucher.setActive(false);
        voucher.setDeletedAt(now);
        voucher.setDeletedByPrincipalId(actor);
        voucher.setDeletionReason(normalizedReason(reason, "deleted_by_request"));
        return true;
    }

    private static void requirePendingShop(Voucher voucher) {
        requireNotDeleted(voucher);
        if (voucher.getCreatorType() != Voucher.CreatorType.SHOP) {
            throw new IllegalArgumentException("Only shop vouchers require approval");
        }
        if (!"PENDING".equalsIgnoreCase(voucher.getApprovalStatus())) {
            throw new PromotionConflictException("Voucher is not pending approval");
        }
    }

    private static void requireNotDeleted(Voucher voucher) {
        if (voucher.getDeletedAt() != null) {
            throw new PromotionConflictException("Deleted voucher cannot be activated");
        }
    }

    private static String normalizedReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason.trim();
    }
}
