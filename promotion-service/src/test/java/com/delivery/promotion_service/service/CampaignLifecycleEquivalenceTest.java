package com.delivery.promotion_service.service;

import com.delivery.promotion_service.dto.CreateVoucherRequest;
import com.delivery.promotion_service.entity.UserVoucher;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.exception.PromotionConflictException;
import com.delivery.promotion_service.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampaignLifecycleEquivalenceTest {
    private final VoucherRepository vouchers = mock(VoucherRepository.class);
    private final UserVoucherRepository wallets = mock(UserVoucherRepository.class);
    private final PromotionOutboxService outbox = mock(PromotionOutboxService.class);
    private final PromotionService service = new PromotionService(vouchers, wallets,
            mock(VoucherGroupRepository.class), mock(VoucherReservationRepository.class), outbox);

    @Test void creationValidatesBeforeDuplicateQueryAndPreservesEnumCause() {
        var request = request(); request.setLayerCode("typo");
        var failure = catchThrowable(() -> service.createVoucher(request));
        assertThat(failure).isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("Voucher layer is invalid");
        assertThat(failure.getCause()).isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("No enum constant com.delivery.promotion_service.service.VoucherLayer.TYPO");
        verifyNoInteractions(vouchers, wallets, outbox);
        request.setCode(" "); request.setName(null);
        assertThatThrownBy(() -> service.createVoucher(request)).isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Voucher code is required");
        verifyNoInteractions(vouchers);
    }

    @Test void creationNormalizesCodeThenQueriesThenFlushesWithIdenticalDefaults() {
        var request = request(); request.setCode(" code "); request.setFundingSource("SHOP");
        request.setLayerCode(" platform_discount ");
        when(vouchers.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.createVoucher(request);
        assertThat(request.getCode()).isEqualTo("CODE");
        assertThat(result.getCode()).isEqualTo("CODE");
        assertThat(result.getLayerCode()).isEqualTo(" PLATFORM_DISCOUNT ");
        assertThat(result.getFundingSource()).isEqualTo("PLATFORM");
        assertThat(result.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(result.getActive()).isTrue();
        assertThat(result.getUsageLimitPerUser()).isEqualTo(2);
        var order = inOrder(vouchers);
        order.verify(vouchers).findByCode("CODE"); order.verify(vouchers).saveAndFlush(result);
        verifyNoInteractions(wallets, outbox);
    }

    @Test void moderationLocksBeforeMappingAndSavesOnlySuccessfulOutcomes() {
        var pending = Voucher.builder().id(1L).creatorType(Voucher.CreatorType.SHOP)
                .approvalStatus("PENDING").active(false).usedQuantity(2).build();
        when(vouchers.findByIdForUpdate(1L)).thenReturn(Optional.of(pending));
        service.approveShopVoucher(1L, 70L);
        var order = inOrder(vouchers); order.verify(vouchers).findByIdForUpdate(1L); order.verify(vouchers).save(pending);
        assertThat(pending.getUsedQuantity()).isEqualTo(2);
        assertThat(pending.getApprovedByPrincipalId()).isEqualTo(70);
        clearInvocations(vouchers);
        assertThatThrownBy(() -> service.rejectShopVoucher(1L, 80L, "x"))
                .isExactlyInstanceOf(PromotionConflictException.class).hasMessage("Voucher is not pending approval");
        verify(vouchers).findByIdForUpdate(1L); verify(vouchers, never()).save(any());
        pending.setCreatorType(Voucher.CreatorType.PLATFORM);
        assertThatThrownBy(() -> service.approveShopVoucher(1L, 80L))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("Only shop vouchers require approval");
        pending.setDeletedAt(LocalDateTime.now());
        assertThatThrownBy(() -> service.approveShopVoucher(1L, 80L))
                .isExactlyInstanceOf(PromotionConflictException.class).hasMessage("Deleted voucher cannot be activated");
        verifyNoInteractions(wallets, outbox);
    }

    @Test void collectionQueriesVoucherThenWalletThenFlushAndDuplicateFailsWithoutSave() {
        var voucher = Voucher.builder().id(1L).creatorType(Voucher.CreatorType.PLATFORM)
                .rewardType(Voucher.RewardType.FIXED).scopeType(Voucher.ScopeType.ALL)
                .discountValue(BigDecimal.ONE).active(true).endTime(LocalDateTime.now().plusDays(1))
                .totalQuantity(10).usedQuantity(0).build();
        when(vouchers.findByCode("CODE")).thenReturn(Optional.of(voucher));
        service.collectVoucher(7L, " code ");
        var order = inOrder(vouchers, wallets);
        order.verify(vouchers).findByCode("CODE");
        order.verify(wallets).findByUserIdAndVoucherId(7L, 1L);
        order.verify(wallets).saveAndFlush(argThat(wallet -> wallet.getStatus() == UserVoucher.Status.SAVED
                && wallet.getUserId().equals(7L) && wallet.getVoucherId().equals(1L)));
        clearInvocations(wallets);
        when(wallets.findByUserIdAndVoucherId(7L,1L)).thenReturn(Optional.of(UserVoucher.builder().build()));
        assertThatThrownBy(() -> service.collectVoucher(7L,"CODE"))
                .isExactlyInstanceOf(PromotionConflictException.class).hasMessage("Voucher already collected");
        verify(wallets,never()).saveAndFlush(any()); verifyNoInteractions(outbox);
    }

    private CreateVoucherRequest request() {
        var request = new CreateVoucherRequest();
        request.setCode("CODE"); request.setName("name"); request.setCreatorType(Voucher.CreatorType.PLATFORM);
        request.setRewardType(Voucher.RewardType.FIXED); request.setScopeType(Voucher.ScopeType.ALL);
        request.setDiscountValue(BigDecimal.ONE); request.setMinOrderValue(BigDecimal.ZERO);
        request.setTotalQuantity(10); request.setUsageLimitPerUser(2);
        request.setStartTime(LocalDateTime.now()); request.setEndTime(LocalDateTime.now().plusDays(1));
        return request;
    }
}
