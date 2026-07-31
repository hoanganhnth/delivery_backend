package com.delivery.promotion_service.service;

import com.delivery.promotion_service.dto.ReserveRequest;
import com.delivery.promotion_service.dto.VoucherReservationResponse;
import com.delivery.promotion_service.entity.UserVoucher;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.entity.VoucherReservation;
import com.delivery.promotion_service.exception.PromotionConflictException;
import com.delivery.promotion_service.repository.UserVoucherRepository;
import com.delivery.promotion_service.repository.VoucherGroupRepository;
import com.delivery.promotion_service.repository.VoucherRepository;
import com.delivery.promotion_service.repository.VoucherReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherReservationServiceTest {
    @Mock VoucherRepository voucherRepository;
    @Mock UserVoucherRepository userVoucherRepository;
    @Mock VoucherGroupRepository voucherGroupRepository;
    @Mock VoucherReservationRepository reservationRepository;
    @Mock PromotionOutboxService outboxService;

    PromotionService service;

    @BeforeEach
    void setUp() {
        service = new PromotionService(voucherRepository, userVoucherRepository,
                voucherGroupRepository, reservationRepository, outboxService);
    }

    @Test
    void reserveUsesLockedCapacityAndReturnsServerCalculatedDiscount() {
        ReserveRequest request = request();
        UserVoucher wallet = wallet();
        Voucher voucher = voucher();
        when(reservationRepository.findById(request.getReservationId())).thenReturn(Optional.empty());
        when(reservationRepository.findByOrderId(request.getOrderId())).thenReturn(Optional.empty());
        when(userVoucherRepository.findByUserIdAndVoucherIdForUpdate(7L, 11L))
                .thenReturn(Optional.of(wallet));
        when(voucherRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(voucher));
        when(reservationRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VoucherReservationResponse response = service.reserveVoucher(request);

        assertThat(response.getState()).isEqualTo(VoucherReservation.State.RESERVED);
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("20000.00");
        assertThat(voucher.getUsedQuantity()).isEqualTo(1);
        assertThat(wallet.getStatus()).isEqualTo(UserVoucher.Status.RESERVED);
        ArgumentCaptor<VoucherReservation> saved = ArgumentCaptor.forClass(VoucherReservation.class);
        verify(reservationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(14));
        verify(outboxService).enqueue(saved.getValue());
    }

    @Test
    void exactReplayIsNoOpButChangedPayloadIsRejected() {
        ReserveRequest request = request();
        VoucherReservation existing = reservation(request);
        when(reservationRepository.findById(request.getReservationId()))
                .thenReturn(Optional.of(existing));

        VoucherReservationResponse replay = service.reserveVoucher(request);
        assertThat(replay.getReservationId()).isEqualTo(request.getReservationId());
        verify(userVoucherRepository, never()).findByUserIdAndVoucherIdForUpdate(any(), any());

        request.setSubtotal(new BigDecimal("100001"));
        assertThatThrownBy(() -> service.reserveVoucher(request))
                .isInstanceOf(PromotionConflictException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void releaseRestoresCapacityExactlyOnce() {
        ReserveRequest request = request();
        VoucherReservation reservation = reservation(request);
        UserVoucher wallet = wallet();
        wallet.setStatus(UserVoucher.Status.RESERVED);
        Voucher voucher = voucher();
        voucher.setUsedQuantity(1);
        when(reservationRepository.findByIdForUpdate(request.getReservationId()))
                .thenReturn(Optional.of(reservation));
        when(userVoucherRepository.findByUserIdAndVoucherIdForUpdate(7L, 11L))
                .thenReturn(Optional.of(wallet));
        when(voucherRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(voucher));

        service.releaseReservation(request.getReservationId(), request.getOrderId());
        service.releaseReservation(request.getReservationId(), request.getOrderId());

        assertThat(reservation.getState()).isEqualTo(VoucherReservation.State.RELEASED);
        assertThat(wallet.getStatus()).isEqualTo(UserVoucher.Status.SAVED);
        assertThat(voucher.getUsedQuantity()).isZero();
        verify(voucherRepository).findByIdForUpdate(11L);
    }

    private ReserveRequest request() {
        return ReserveRequest.builder()
                .reservationId(UUID.randomUUID())
                .orderId(101L)
                .userId(7L)
                .voucherId(11L)
                .restaurantId(9L)
                .subtotal(new BigDecimal("100000"))
                .shippingFee(new BigDecimal("15000"))
                .build();
    }

    private UserVoucher wallet() {
        return UserVoucher.builder().id(1L).userId(7L).voucherId(11L)
                .status(UserVoucher.Status.SAVED).build();
    }

    private Voucher voucher() {
        return Voucher.builder().id(11L).code("SAVE20").active(true)
                .creatorType(Voucher.CreatorType.PLATFORM)
                .scopeType(Voucher.ScopeType.SHOP).scopeRefId(9L)
                .rewardType(Voucher.RewardType.PERCENTAGE)
                .discountValue(new BigDecimal("20")).maxDiscountValue(new BigDecimal("30000"))
                .minOrderValue(new BigDecimal("50000"))
                .startTime(LocalDateTime.now().minusMinutes(1))
                .endTime(LocalDateTime.now().plusHours(1))
                .totalQuantity(10).usedQuantity(0).build();
    }

    private VoucherReservation reservation(ReserveRequest request) {
        return VoucherReservation.builder()
                .reservationId(request.getReservationId()).orderId(request.getOrderId())
                .userId(request.getUserId()).voucherId(request.getVoucherId())
                .restaurantId(request.getRestaurantId()).subtotal(request.getSubtotal())
                .shippingFee(request.getShippingFee()).discountAmount(new BigDecimal("20000.00"))
                .state(VoucherReservation.State.RESERVED)
                .expiresAt(LocalDateTime.now().plusMinutes(15)).build();
    }
}
