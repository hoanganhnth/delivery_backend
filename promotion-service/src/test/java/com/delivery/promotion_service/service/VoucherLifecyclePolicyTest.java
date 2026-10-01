package com.delivery.promotion_service.service;

import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.exception.PromotionConflictException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class VoucherLifecyclePolicyTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Test
    void approvalSetsAuditAndClearsRejectionWithoutChangingCommercialPolicy() {
        var voucher = pending();
        voucher.setRejectionReason("old");
        VoucherLifecyclePolicy.approve(voucher, 70L, now);
        assertThat(voucher.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(voucher.getActive()).isTrue();
        assertThat(voucher.getApprovedByPrincipalId()).isEqualTo(70);
        assertThat(voucher.getApprovedAt()).isEqualTo(now);
        assertThat(voucher.getRejectionReason()).isNull();
        assertThat(voucher.getUsedQuantity()).isEqualTo(2);
        assertThat(voucher.getTotalQuantity()).isEqualTo(10);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", " reason "})
    void rejectionNormalizesReasonAndDisablesVoucher(String reason) {
        var voucher = pending();
        VoucherLifecyclePolicy.reject(voucher, 70L, reason, now);
        assertThat(voucher.getActive()).isFalse();
        assertThat(voucher.getApprovalStatus()).isEqualTo("REJECTED");
        assertThat(voucher.getApprovedAt()).isEqualTo(now);
        assertThat(voucher.getApprovedByPrincipalId()).isEqualTo(70);
        assertThat(voucher.getRejectionReason()).isEqualTo(reason == null || reason.isBlank() ? "Rejected by admin" : "reason");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"APPROVED", "REJECTED"})
    void nonpendingVoucherCannotBeModerated(String status) {
        var voucher = pending();
        voucher.setApprovalStatus(status);
        assertThatThrownBy(() -> VoucherLifecyclePolicy.approve(voucher, 70L, now)).isInstanceOf(PromotionConflictException.class);
        assertThatThrownBy(() -> VoucherLifecyclePolicy.reject(voucher, 70L, "reason", now)).isInstanceOf(PromotionConflictException.class);
        assertThat(voucher.getApprovedAt()).isNull();
    }

    @Test
    void nonshopVoucherCannotBeModerated() {
        var voucher = pending();
        voucher.setCreatorType(Voucher.CreatorType.PLATFORM);
        assertThatThrownBy(() -> VoucherLifecyclePolicy.approve(voucher, 70L, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VoucherLifecyclePolicy.reject(voucher, 70L, "reason", now)).isInstanceOf(IllegalArgumentException.class);
        assertThat(voucher.getApprovalStatus()).isEqualTo("PENDING");
    }

    @Test
    void retirementFencesApprovalRejectionAndActivationButAllowsDeactivation() {
        var voucher = pending();
        VoucherLifecyclePolicy.retire(voucher, 70L, "reason", now);
        assertThatThrownBy(() -> VoucherLifecyclePolicy.approve(voucher, 80L, now)).isInstanceOf(PromotionConflictException.class);
        assertThatThrownBy(() -> VoucherLifecyclePolicy.reject(voucher, 80L, "new", now)).isInstanceOf(PromotionConflictException.class);
        assertThatThrownBy(() -> VoucherLifecyclePolicy.setActive(voucher, true)).isInstanceOf(PromotionConflictException.class);
        VoucherLifecyclePolicy.setActive(voucher, false);
        assertThat(voucher.getApprovalStatus()).isEqualTo("PENDING");
        assertThat(voucher.getDeletedByPrincipalId()).isEqualTo(70);
        assertThat(voucher.getDeletionReason()).isEqualTo("reason");
    }

    @Test
    void activationRequiresApprovalAndDoesNotOverwriteModerationAudit() {
        var voucher = pending();
        assertThatThrownBy(() -> VoucherLifecyclePolicy.setActive(voucher, true)).isInstanceOf(PromotionConflictException.class);
        VoucherLifecyclePolicy.approve(voucher, 70L, now);
        VoucherLifecyclePolicy.setActive(voucher, false);
        VoucherLifecyclePolicy.setActive(voucher, true);
        assertThat(voucher.getActive()).isTrue();
        assertThat(voucher.getApprovedAt()).isEqualTo(now);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", " reason "})
    void retirementNormalizesReasonAndRetryPreservesFirstAudit(String reason) {
        var voucher = pending();
        assertThat(VoucherLifecyclePolicy.retire(voucher, 70L, reason, now)).isTrue();
        assertThat(VoucherLifecyclePolicy.retire(voucher, 80L, "new", now.plusDays(1))).isFalse();
        assertThat(voucher.getDeletedAt()).isEqualTo(now);
        assertThat(voucher.getDeletedByPrincipalId()).isEqualTo(70);
        assertThat(voucher.getDeletionReason()).isEqualTo(reason == null || reason.isBlank() ? "deleted_by_request" : "reason");
        assertThat(voucher.getActive()).isFalse();
        assertThat(voucher.getUsedQuantity()).isEqualTo(2);
    }

    private Voucher pending() {
        return Voucher.builder().creatorType(Voucher.CreatorType.SHOP).approvalStatus("PENDING")
                .active(false).totalQuantity(10).usedQuantity(2).build();
    }
}
