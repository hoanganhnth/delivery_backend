package com.delivery.promotion_service.service;

import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.entity.UserVoucher;
import com.delivery.promotion_service.dto.CartContextRequest;
import com.delivery.promotion_service.dto.CreateVoucherRequest;
import com.delivery.promotion_service.repository.UserVoucherRepository;
import com.delivery.promotion_service.repository.VoucherGroupRepository;
import com.delivery.promotion_service.repository.VoucherRepository;
import com.delivery.promotion_service.repository.VoucherReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.dao.DataIntegrityViolationException;
import com.delivery.promotion_service.exception.PromotionConflictException;

@ExtendWith(MockitoExtension.class)
class PromotionServiceQuerySafetyTest {

    @Mock VoucherRepository voucherRepository;
    @Mock UserVoucherRepository userVoucherRepository;
    @Mock VoucherGroupRepository voucherGroupRepository;
    @Mock VoucherReservationRepository voucherReservationRepository;
    @Mock PromotionOutboxService outboxService;

    @Test
    void compatibilityListsCapRepositoryQueriesAtOneHundred() {
        PromotionService service = new PromotionService(
                voucherRepository, userVoucherRepository, voucherGroupRepository, voucherReservationRepository, outboxService);
        Pageable firstHundred = Pageable.ofSize(100);
        when(voucherRepository.findAll(firstHundred)).thenReturn(new PageImpl<>(List.of()));
        when(voucherRepository.findByCreatorTypeAndCreatorId(
                eq(Voucher.CreatorType.MERCHANT), eq(7L), eq(firstHundred)))
                .thenReturn(List.of());

        assertEquals(List.of(), service.listAllVouchers());
        assertEquals(List.of(), service.listMerchantVouchers(7L));

        verify(voucherRepository).findAll(firstHundred);
        verify(voucherRepository).findByCreatorTypeAndCreatorId(
                Voucher.CreatorType.MERCHANT, 7L, firstHundred);
    }

    @Test
    void userVoucherListsAreBoundedAndCalculateUsesOneBatchLookup() {
        PromotionService service = new PromotionService(
                voucherRepository, userVoucherRepository, voucherGroupRepository, voucherReservationRepository, outboxService);
        Pageable firstHundred = Pageable.ofSize(100);
        UserVoucher collected = UserVoucher.builder()
                .userId(7L)
                .voucherId(11L)
                .status(UserVoucher.Status.SAVED)
                .build();
        Voucher voucher = Voucher.builder()
                .id(11L)
                .creatorType(Voucher.CreatorType.PLATFORM)
                .rewardType(Voucher.RewardType.FIXED)
                .discountValue(BigDecimal.ONE)
                .layerCode(VoucherLayer.PLATFORM_DISCOUNT.name())
                .active(true)
                .endTime(LocalDateTime.now().plusDays(1))
                .totalQuantity(10)
                .usedQuantity(0)
                .minOrderValue(BigDecimal.ZERO)
                .scopeType(Voucher.ScopeType.ALL)
                .build();
        when(userVoucherRepository.findByUserIdAndStatus(
                7L, UserVoucher.Status.SAVED, firstHundred))
                .thenReturn(List.of(collected));
        when(voucherRepository.findAllById(List.of(11L))).thenReturn(List.of(voucher));
        CartContextRequest request = CartContextRequest.builder()
                .userId(7L)
                .shopId(9L)
                .subTotal(BigDecimal.TEN)
                .shippingFee(BigDecimal.ONE)
                .build();

        service.calculate(request);
        assertEquals(List.of(voucher), service.getCollectedVouchers(7L));

        verify(userVoucherRepository, org.mockito.Mockito.times(2))
                .findByUserIdAndStatus(7L, UserVoucher.Status.SAVED, firstHundred);
        verify(voucherRepository, org.mockito.Mockito.times(2)).findAllById(List.of(11L));
        verify(voucherRepository, never()).findById(11L);
    }

    @Test
    void collectRejectsVoucherBeforeStartTime() {
        PromotionService service = new PromotionService(
                voucherRepository, userVoucherRepository, voucherGroupRepository, voucherReservationRepository, outboxService);
        Voucher voucher = Voucher.builder()
                .id(11L).active(true)
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusDays(1))
                .usedQuantity(0).totalQuantity(10)
                .build();
        when(voucherRepository.findByCode("LATER")).thenReturn(java.util.Optional.of(voucher));

        assertThrows(IllegalArgumentException.class, () -> service.collectVoucher(7L, "LATER"));

        verifyNoInteractions(userVoucherRepository);
    }

    @Test
    void collectRejectsVoucherWithoutEndTimeAsMalformed() {
        PromotionService service = new PromotionService(
                voucherRepository, userVoucherRepository, voucherGroupRepository, voucherReservationRepository, outboxService);
        Voucher voucher = Voucher.builder()
                .id(11L).active(true)
                .creatorType(Voucher.CreatorType.PLATFORM)
                .rewardType(Voucher.RewardType.FIXED)
                .discountValue(BigDecimal.ONE)
                .scopeType(Voucher.ScopeType.ALL)
                .layerCode(VoucherLayer.PLATFORM_DISCOUNT.name())
                .usedQuantity(0).totalQuantity(10)
                .build();
        when(voucherRepository.findByCode("NOEND")).thenReturn(java.util.Optional.of(voucher));

        assertThrows(IllegalArgumentException.class, () -> service.collectVoucher(7L, "NOEND"));

        verifyNoInteractions(userVoucherRepository);
    }

    @Test
    void concurrentCollectUniqueViolationBecomesConflict() {
        PromotionService service = new PromotionService(
                voucherRepository, userVoucherRepository, voucherGroupRepository, voucherReservationRepository, outboxService);
        Voucher voucher = Voucher.builder()
                .id(11L).active(true)
                .creatorType(Voucher.CreatorType.PLATFORM)
                .rewardType(Voucher.RewardType.FIXED)
                .discountValue(BigDecimal.ONE)
                .scopeType(Voucher.ScopeType.ALL)
                .layerCode(VoucherLayer.PLATFORM_DISCOUNT.name())
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusDays(1))
                .usedQuantity(0).totalQuantity(10)
                .build();
        when(voucherRepository.findByCode("WELCOME")).thenReturn(java.util.Optional.of(voucher));
        when(userVoucherRepository.findByUserIdAndVoucherId(7L, 11L))
                .thenReturn(java.util.Optional.empty());
        when(userVoucherRepository.saveAndFlush(any(UserVoucher.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(PromotionConflictException.class,
                () -> service.collectVoucher(7L, "WELCOME"));
    }

    @Test
    void concurrentVoucherCodeViolationBecomesConflict() {
        PromotionService service = new PromotionService(
                voucherRepository, userVoucherRepository, voucherGroupRepository, voucherReservationRepository, outboxService);
        CreateVoucherRequest request = validPlatformVoucherRequest();
        when(voucherRepository.findByCode("WELCOME")).thenReturn(java.util.Optional.empty());
        when(voucherRepository.saveAndFlush(any(Voucher.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(PromotionConflictException.class, () -> service.createVoucher(request));
    }

    @Test
    void voucherCreationRejectsInvertedTimeWindowBeforeRepositoryAccess() {
        PromotionService service = new PromotionService(
                voucherRepository, userVoucherRepository, voucherGroupRepository, voucherReservationRepository, outboxService);
        CreateVoucherRequest request = validPlatformVoucherRequest();
        request.setStartTime(LocalDateTime.now().plusDays(2));
        request.setEndTime(LocalDateTime.now().plusDays(1));

        assertThrows(IllegalArgumentException.class, () -> service.createVoucher(request));

        verifyNoInteractions(voucherRepository, userVoucherRepository, voucherGroupRepository);
    }

    @Test
    void voucherCreationRejectsLegacyMerchantAndCategoryOwnership() {
        PromotionService service = new PromotionService(
                voucherRepository, userVoucherRepository, voucherGroupRepository,
                voucherReservationRepository, outboxService);
        CreateVoucherRequest request = validPlatformVoucherRequest();
        request.setCreatorType(Voucher.CreatorType.MERCHANT);
        request.setCreatorId(7L);

        assertThrows(IllegalArgumentException.class, () -> service.createVoucher(request));

        request.setCreatorType(Voucher.CreatorType.PLATFORM);
        request.setCreatorId(null);
        request.setScopeType(Voucher.ScopeType.CATEGORY);
        request.setScopeRefId(12L);
        assertThrows(IllegalArgumentException.class, () -> service.createVoucher(request));
        verifyNoInteractions(voucherRepository);
    }

    @Test
    void restaurantScopedVoucherRequiresCanonicalRestaurantIdentity() {
        PromotionService service = new PromotionService(
                voucherRepository, userVoucherRepository, voucherGroupRepository,
                voucherReservationRepository, outboxService);
        CreateVoucherRequest request = validPlatformVoucherRequest();
        request.setScopeType(Voucher.ScopeType.SHOP);

        assertThrows(IllegalArgumentException.class, () -> service.createVoucher(request));

        request.setScopeRefId(9L);
        when(voucherRepository.findByCode("WELCOME")).thenReturn(java.util.Optional.empty());
        when(voucherRepository.saveAndFlush(any(Voucher.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Voucher created = service.createVoucher(request);
        org.assertj.core.api.Assertions.assertThat(created.getScopeType()).isEqualTo(Voucher.ScopeType.SHOP);
        org.assertj.core.api.Assertions.assertThat(created.getScopeRefId()).isEqualTo(9L);
    }

    @Test
    void voucherCreationRejectsInvalidLayerAndNegativeCapBeforePersistence() {
        PromotionService service = new PromotionService(
                voucherRepository, userVoucherRepository, voucherGroupRepository,
                voucherReservationRepository, outboxService);
        CreateVoucherRequest request = validPlatformVoucherRequest();
        request.setLayerCode("PLATFROM_DISCOUNT");

        assertThrows(IllegalArgumentException.class, () -> service.createVoucher(request));

        request.setLayerCode(null);
        request.setMaxDiscountValue(new BigDecimal("-1"));
        assertThrows(IllegalArgumentException.class, () -> service.createVoucher(request));
        verifyNoInteractions(voucherRepository);
    }

    private CreateVoucherRequest validPlatformVoucherRequest() {
        CreateVoucherRequest request = new CreateVoucherRequest();
        request.setCode("WELCOME");
        request.setName("Welcome voucher");
        request.setCreatorType(Voucher.CreatorType.PLATFORM);
        request.setRewardType(Voucher.RewardType.FIXED);
        request.setDiscountValue(BigDecimal.TEN);
        request.setScopeType(Voucher.ScopeType.ALL);
        request.setTotalQuantity(10);
        request.setUsageLimitPerUser(1);
        request.setMinOrderValue(BigDecimal.ZERO);
        request.setStartTime(LocalDateTime.now());
        request.setEndTime(LocalDateTime.now().plusDays(1));
        return request;
    }
}
