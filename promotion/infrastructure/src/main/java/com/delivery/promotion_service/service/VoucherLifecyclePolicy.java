package com.delivery.promotion_service.service;

import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.exception.PromotionConflictException;
import java.time.LocalDateTime;

/** Approval, activation and retirement transitions; caller owns locks and transaction. */
final class VoucherLifecyclePolicy {
    private VoucherLifecyclePolicy() {}

    static void approve(Voucher voucher, Long actor, LocalDateTime now) {
        var outcome = checked(com.delivery.promotion.domain.VoucherLifecyclePolicy.approve(VoucherDomainMapper.snapshot(voucher)));
        voucher.setApprovalStatus(outcome.approvalStatus());
        voucher.setApprovedByPrincipalId(actor);
        voucher.setApprovedAt(now);
        voucher.setRejectionReason(null);
        voucher.setActive(outcome.active());
    }

    static void reject(Voucher voucher, Long actor, String reason, LocalDateTime now) {
        var outcome = checked(com.delivery.promotion.domain.VoucherLifecyclePolicy.reject(VoucherDomainMapper.snapshot(voucher), reason));
        voucher.setApprovalStatus(outcome.approvalStatus());
        voucher.setApprovedByPrincipalId(actor);
        voucher.setApprovedAt(now);
        voucher.setRejectionReason(outcome.reason());
        voucher.setActive(outcome.active());
    }

    static void setActive(Voucher voucher, boolean active) {
        var outcome = checked(com.delivery.promotion.domain.VoucherLifecyclePolicy.setActive(VoucherDomainMapper.snapshot(voucher), active));
        voucher.setActive(outcome.active());
    }

    static boolean retire(Voucher voucher, Long actor, String reason, LocalDateTime now) {
        var outcome = checked(com.delivery.promotion.domain.VoucherLifecyclePolicy.retire(VoucherDomainMapper.snapshot(voucher), reason));
        if (!outcome.changed()) return false;
        voucher.setActive(outcome.active());
        voucher.setDeletedAt(now);
        voucher.setDeletedByPrincipalId(actor);
        voucher.setDeletionReason(outcome.reason());
        return true;
    }

    private static com.delivery.promotion.domain.VoucherLifecyclePolicy.Outcome checked(
            com.delivery.promotion.domain.VoucherLifecyclePolicy.Outcome outcome) {
        if (outcome.failure() != null) {
            if (outcome.conflict()) throw new PromotionConflictException(outcome.failure());
            throw new IllegalArgumentException(outcome.failure());
        }
        return outcome;
    }
}
