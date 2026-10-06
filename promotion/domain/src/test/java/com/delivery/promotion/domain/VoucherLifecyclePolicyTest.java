package com.delivery.promotion.domain;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class VoucherLifecyclePolicyTest {
    private TestVoucher pending() {
        return TestVoucher.builder().creatorType(Voucher.CreatorType.SHOP)
                .approvalStatus("pending").active(false).build();
    }

    @Test void approvalAndOwnedCreationReturnImmutableMutations() {
        var voucher = pending();
        var approved = VoucherLifecyclePolicy.approve(voucher);
        assertThat(approved).isEqualTo(VoucherLifecyclePolicy.approveNewOwnedShop());
        assertThat(approved.changed()).isTrue();
        assertThat(approved.approvalStatus()).isEqualTo("APPROVED");
        assertThat(approved.active()).isTrue();
        assertThat(approved.reason()).isNull();
        assertThat(approved.failure()).isNull();
        assertThat(voucher.getApprovalStatus()).isEqualTo("pending");
        assertThat(voucher.getActive()).isFalse();
    }

    @ParameterizedTest @NullSource @ValueSource(strings={"", " ", " reason "})
    void reasonDefaultsAndRetirementReplay(String reason) {
        var voucher = pending();
        var rejected = VoucherLifecyclePolicy.reject(voucher, reason);
        assertThat(rejected.approvalStatus()).isEqualTo("REJECTED");
        assertThat(rejected.active()).isFalse();
        assertThat(rejected.reason()).isEqualTo(reason == null || reason.isBlank() ? "Rejected by admin" : "reason");
        var retired = VoucherLifecyclePolicy.retire(voucher, reason);
        assertThat(retired.changed()).isTrue();
        assertThat(retired.active()).isFalse();
        assertThat(retired.reason()).isEqualTo(reason == null || reason.isBlank() ? "deleted_by_request" : "reason");
        voucher.setDeletedAt(LocalDateTime.now());
        assertThat(VoucherLifecyclePolicy.retire(voucher, "new").changed()).isFalse();
    }

    @Test void deletedGuardPrecedesCreatorAndStatusForBothModerationActions() {
        var voucher = pending();
        voucher.setCreatorType(Voucher.CreatorType.PLATFORM);
        voucher.setApprovalStatus("REJECTED");
        voucher.setDeletedAt(LocalDateTime.now());
        assertFailure(VoucherLifecyclePolicy.approve(voucher), "Deleted voucher cannot be activated", true);
        assertFailure(VoucherLifecyclePolicy.reject(voucher, "x"), "Deleted voucher cannot be activated", true);
        assertFailure(VoucherLifecyclePolicy.setActive(voucher, true), "Deleted voucher cannot be activated", true);
        assertThat(VoucherLifecyclePolicy.setActive(voucher, false).active()).isFalse();
    }

    @ParameterizedTest @NullSource @ValueSource(strings={"APPROVED", "REJECTED", ""})
    void creatorGuardPrecedesPendingGuard(String status) {
        var voucher = pending();
        voucher.setApprovalStatus(status);
        voucher.setCreatorType(null);
        assertFailure(VoucherLifecyclePolicy.approve(voucher), "Only shop vouchers require approval", false);
        assertFailure(VoucherLifecyclePolicy.reject(voucher, "x"), "Only shop vouchers require approval", false);
        voucher.setCreatorType(Voucher.CreatorType.SHOP);
        assertFailure(VoucherLifecyclePolicy.approve(voucher), "Voucher is not pending approval", true);
        assertFailure(VoucherLifecyclePolicy.reject(voucher, "x"), "Voucher is not pending approval", true);
    }

    @ParameterizedTest @NullSource @ValueSource(strings={"approved", "APPROVED"})
    void activationAllowsLegacyNullApproval(String status) {
        var voucher = pending();
        voucher.setApprovalStatus(status);
        assertThat(VoucherLifecyclePolicy.setActive(voucher, true).active()).isTrue();
    }

    @Test void activationDoesNotAddTimeOrQuotaGuards() {
        var voucher = pending();
        assertFailure(VoucherLifecyclePolicy.setActive(voucher, true), "Voucher is not approved", true);
        voucher.setApprovalStatus("APPROVED");
        voucher.setEndTime(LocalDateTime.now().minusDays(1));
        voucher.setUsedQuantity(10); voucher.setTotalQuantity(10);
        assertThat(VoucherLifecyclePolicy.setActive(voucher, true).active()).isTrue();
    }

    private void assertFailure(VoucherLifecyclePolicy.Outcome result, String message, boolean conflict) {
        assertThat(result.failure()).isEqualTo(message);
        assertThat(result.conflict()).isEqualTo(conflict);
        assertThat(result.changed()).isFalse();
    }
}
