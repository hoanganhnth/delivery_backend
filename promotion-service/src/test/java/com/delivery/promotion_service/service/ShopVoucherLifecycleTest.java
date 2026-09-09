package com.delivery.promotion_service.service;

import com.delivery.promotion_service.dto.CreateVoucherRequest;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.exception.PromotionConflictException;
import com.delivery.promotion_service.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopVoucherLifecycleTest {
    private final VoucherRepository vouchers = mock(VoucherRepository.class);
    private final RestaurantOwnershipClient ownership = mock(RestaurantOwnershipClient.class);
    private PromotionService service;

    @BeforeEach void setup() {
        service = new PromotionService(vouchers, mock(UserVoucherRepository.class),
                mock(VoucherGroupRepository.class), mock(VoucherReservationRepository.class),
                mock(PromotionOutboxService.class));
        ReflectionTestUtils.setField(service, "restaurantOwnershipClient", ownership);
    }

    @Test void ownedShopVoucherIsImmediatelyApprovedAndShopFunded() {
        when(ownership.isOwnedBy(36L, 159L, 151L)).thenReturn(true);
        when(vouchers.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = request();
        request.setFundingSource("PLATFORM");
        request.setScopeType(Voucher.ScopeType.ALL);
        Voucher result = service.createShopVoucher(request, 159L, 151L);
        assertEquals("APPROVED", result.getApprovalStatus());
        assertTrue(result.getActive());
        assertEquals("SHOP", result.getFundingSource());
        assertEquals("SHOP_DISCOUNT", result.getLayerCode());
        assertEquals(36L, result.getScopeRefId());
        assertEquals(159L, result.getOwnerPrincipalId());
        assertNotNull(result.getApprovedAt());
        assertNull(result.getApprovedByPrincipalId(), "automatic approval must not impersonate an admin");
    }

    @Test void anotherRestaurantCannotBeAutoApproved() {
        assertThrows(PromotionConflictException.class,
                () -> service.createShopVoucher(request(), 159L, 151L));
        verifyNoInteractions(vouchers);
    }

    @Test void rejectedHistoricalVoucherCannotBeResumed() {
        var voucher = Voucher.builder().id(9L).approvalStatus("REJECTED").active(false).build();
        when(vouchers.findByIdForUpdate(9L)).thenReturn(Optional.of(voucher));
        assertThrows(PromotionConflictException.class, () -> service.setVoucherActive(9L, true));
        assertFalse(voucher.getActive());
        verify(vouchers, never()).save(any());
    }

    @Test void deletionUsesLockedCurrentQuotaAndPreservesUsage() {
        var current = Voucher.builder().id(9L).usedQuantity(1).active(true).build();
        // A stale, unlocked read would lose a concurrent reservation's quota.
        when(vouchers.findById(9L)).thenReturn(Optional.of(
                Voucher.builder().id(9L).usedQuantity(0).active(true).build()));
        when(vouchers.findByIdForUpdate(9L)).thenReturn(Optional.of(current));
        service.deleteVoucher(9L);
        verify(vouchers).save(current);
        assertFalse(current.getActive());
        assertEquals(1, current.getUsedQuantity());
        verify(vouchers, never()).findById(9L);
    }

    private CreateVoucherRequest request() {
        var request = new CreateVoucherRequest();
        request.setCode("SHOP10"); request.setName("Shop discount");
        request.setRestaurantId(36L);
        request.setRewardType(Voucher.RewardType.FIXED);
        request.setDiscountValue(BigDecimal.TEN);
        request.setTotalQuantity(10); request.setUsageLimitPerUser(1);
        request.setMinOrderValue(BigDecimal.ZERO);
        request.setStartTime(LocalDateTime.now().minusMinutes(1));
        request.setEndTime(LocalDateTime.now().plusDays(1));
        return request;
    }
}
