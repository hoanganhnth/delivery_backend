package com.delivery.promotion_service.service;

import com.delivery.promotion_service.dto.CalculateResponse;
import com.delivery.promotion_service.dto.CartContextRequest;
import com.delivery.promotion_service.dto.ReserveRequest;
import com.delivery.promotion_service.entity.UserVoucher;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.entity.VoucherGroup;
import com.delivery.promotion_service.entity.VoucherReservation;
import com.delivery.promotion_service.repository.UserVoucherRepository;
import com.delivery.promotion_service.repository.VoucherGroupRepository;
import com.delivery.promotion_service.repository.VoucherRepository;
import com.delivery.promotion_service.repository.VoucherReservationRepository;
import com.delivery.promotion_service.dto.VoucherReservationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import com.delivery.promotion_service.exception.PromotionConflictException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import com.delivery.promotion_service.dto.CreateVoucherRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionService {

    private static final int COMPATIBILITY_LIST_LIMIT = 100;

    private final VoucherRepository voucherRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final VoucherGroupRepository voucherGroupRepository;
    private final VoucherReservationRepository voucherReservationRepository;
    private final PromotionOutboxService outboxService;

    @Transactional
    public Voucher createVoucher(CreateVoucherRequest request) {
        validateCreateVoucherRequest(request);
        if (voucherRepository.findByCode(request.getCode()).isPresent()) {
            throw new PromotionConflictException("Voucher code already exists");
        }
        Voucher voucher = Voucher.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .creatorType(request.getCreatorType())
                .creatorId(request.getCreatorId())
                .rewardType(request.getRewardType())
                .discountValue(request.getDiscountValue())
                .maxDiscountValue(request.getMaxDiscountValue())
                .scopeType(request.getScopeType())
                .scopeRefId(request.getScopeRefId())
                .totalQuantity(request.getTotalQuantity())
                .usageLimitPerUser(request.getUsageLimitPerUser())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .minOrderValue(request.getMinOrderValue())
                .voucherGroupId(request.getVoucherGroupId())
                .customerSegment(request.getCustomerSegment())
                .active(true)
                .build();
        try {
            return voucherRepository.saveAndFlush(voucher);
        } catch (DataIntegrityViolationException ex) {
            throw new PromotionConflictException("Voucher code already exists", ex);
        }
    }

    @Transactional
    public void collectVoucher(Long userId, String voucherCode) {
        validatePositiveId(userId, "userId");
        if (voucherCode == null || voucherCode.isBlank()) {
            throw new IllegalArgumentException("Voucher code is required");
        }
        Voucher voucher = voucherRepository.findByCode(voucherCode)
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));

        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.TRUE.equals(voucher.getActive()) || voucher.getEndTime().isBefore(now)) {
            throw new IllegalArgumentException("Voucher is expired or inactive");
        }
        if (voucher.getStartTime() != null && now.isBefore(voucher.getStartTime())) {
            throw new IllegalArgumentException("Voucher is not active yet");
        }

        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            throw new IllegalArgumentException("Voucher is out of stock");
        }

        Optional<UserVoucher> existing = userVoucherRepository.findByUserIdAndVoucherId(userId, voucher.getId());
        if (existing.isPresent()) {
            throw new PromotionConflictException("Voucher already collected");
        }

        UserVoucher userVoucher = UserVoucher.builder()
                .userId(userId)
                .voucherId(voucher.getId())
                .status(UserVoucher.Status.SAVED)
                .build();
        
        try {
            userVoucherRepository.saveAndFlush(userVoucher);
        } catch (DataIntegrityViolationException ex) {
            throw new PromotionConflictException("Voucher already collected", ex);
        }
    }

    @Transactional(readOnly = true)
    public CalculateResponse calculate(CartContextRequest request) {
        validateCalculateRequest(request);
        List<UserVoucher> savedVouchers = userVoucherRepository.findByUserIdAndStatus(
                request.getUserId(), UserVoucher.Status.SAVED,
                PageRequest.of(0, COMPATIBILITY_LIST_LIMIT));
        Map<Long, Voucher> vouchersById = voucherRepository.findAllById(savedVouchers.stream()
                        .map(UserVoucher::getVoucherId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Voucher::getId, voucher -> voucher));
        
        List<CalculateResponse.VoucherInfo> available = new ArrayList<>();
        List<CalculateResponse.UnavailableVoucherInfo> unavailable = new ArrayList<>();

        for (UserVoucher uv : savedVouchers) {
            Voucher voucher = vouchersById.get(uv.getVoucherId());
            if (voucher == null) continue;

            String unavailReason = checkVoucherAvailability(voucher, request);
            if (unavailReason == null) {
                available.add(CalculateResponse.VoucherInfo.builder()
                        .id(voucher.getId())
                        .code(voucher.getCode())
                        .name(voucher.getName())
                        .rewardType(voucher.getRewardType())
                        .discountValue(voucher.getDiscountValue())
                        .voucherGroupId(voucher.getVoucherGroupId())
                        .build());
            } else {
                unavailable.add(CalculateResponse.UnavailableVoucherInfo.builder()
                        .id(voucher.getId())
                        .code(voucher.getCode())
                        .name(voucher.getName())
                        .reason(unavailReason)
                        .build());
            }
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (request.getSelectedVoucherId() != null) {
            Voucher selected = vouchersById.get(request.getSelectedVoucherId());
            if (selected == null) throw new IllegalArgumentException("Selected voucher is not saved in this wallet");
            String unavailableReason = checkVoucherAvailability(selected, request);
            if (unavailableReason != null) throw new IllegalArgumentException(unavailableReason);
            discount = calculateDiscount(selected, request.getSubTotal(), request.getShippingFee());
        }
        BigDecimal total = request.getSubTotal().add(request.getShippingFee()).subtract(discount);
        return CalculateResponse.builder()
                .availableVouchers(available)
                .unavailableVouchers(unavailable)
                .finalSubTotal(request.getSubTotal())
                .finalShippingFee(request.getShippingFee())
                .totalDiscount(discount)
                .totalAmount(total)
                .build();
    }

    private String checkVoucherAvailability(Voucher voucher, CartContextRequest request) {
        if (voucher.getCreatorType() != Voucher.CreatorType.PLATFORM) {
            return "Voucher ownership is not supported for checkout";
        }
        if (voucher.getScopeType() != Voucher.ScopeType.ALL
                && voucher.getScopeType() != Voucher.ScopeType.SHOP) {
            return "Voucher scope is not supported for checkout";
        }
        if (!Boolean.TRUE.equals(voucher.getActive())) return "Voucher is inactive";
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartTime() != null && now.isBefore(voucher.getStartTime())) return "Voucher is not active yet";
        if (voucher.getEndTime() != null && voucher.getEndTime().isBefore(now)) return "Voucher expired";
        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) return "Out of stock";
        BigDecimal minOrderValue = voucher.getMinOrderValue() == null ? BigDecimal.ZERO : voucher.getMinOrderValue();
        if (request.getSubTotal().compareTo(minOrderValue) < 0) {
            return "Need " + minOrderValue.subtract(request.getSubTotal()) + " more to use";
        }
        if (voucher.getScopeType() == Voucher.ScopeType.SHOP
                && (voucher.getScopeRefId() == null || !voucher.getScopeRefId().equals(request.getShopId()))) {
            return "Not applicable for this shop";
        }
        return null; // Available
    }

    @Transactional
    public VoucherReservationResponse reserveVoucher(ReserveRequest request) {
        validateReserveRequest(request);
        Optional<VoucherReservation> sameId = voucherReservationRepository.findById(request.getReservationId());
        if (sameId.isPresent()) {
            requireSameReservation(sameId.get(), request);
            return VoucherReservationResponse.from(sameId.get());
        }
        Optional<VoucherReservation> sameOrder = voucherReservationRepository.findByOrderId(request.getOrderId());
        if (sameOrder.isPresent()) {
            requireSameReservation(sameOrder.get(), request);
            return VoucherReservationResponse.from(sameOrder.get());
        }

        UserVoucher userVoucher = userVoucherRepository.findByUserIdAndVoucherIdForUpdate(
                        request.getUserId(), request.getVoucherId())
                .orElseThrow(() -> new IllegalArgumentException("User has not collected voucher "
                        + request.getVoucherId()));
        if (userVoucher.getStatus() != UserVoucher.Status.SAVED) {
            throw new PromotionConflictException("Voucher is already reserved or used");
        }

        Voucher voucher = voucherRepository.findByIdForUpdate(request.getVoucherId())
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
        String unavailable = checkVoucherAvailability(voucher,
                CartContextRequest.builder()
                        .userId(request.getUserId())
                        .shopId(request.getRestaurantId())
                        .subTotal(request.getSubtotal())
                        .shippingFee(request.getShippingFee())
                        .build());
        if (unavailable != null) throw new IllegalArgumentException(unavailable);

        BigDecimal discount = calculateDiscount(voucher, request.getSubtotal(), request.getShippingFee());
        LocalDateTime now = LocalDateTime.now();
        VoucherReservation reservation = VoucherReservation.builder()
                .reservationId(request.getReservationId())
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .voucherId(request.getVoucherId())
                .restaurantId(request.getRestaurantId())
                .subtotal(request.getSubtotal())
                .shippingFee(request.getShippingFee())
                .discountAmount(discount)
                .state(VoucherReservation.State.RESERVED)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .createdAt(now)
                .updatedAt(now)
                .build();

        userVoucher.setStatus(UserVoucher.Status.RESERVED);
        userVoucher.setOrderId(request.getOrderId());
        userVoucher.setUsedAt(null);
        voucher.setUsedQuantity(voucher.getUsedQuantity() + 1);
        try {
            voucherReservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            throw new PromotionConflictException("Conflicting voucher reservation", ex);
        }
        outboxService.enqueue(reservation);
        return VoucherReservationResponse.from(reservation);
    }

    @Transactional
    public VoucherReservationResponse commitReservation(UUID reservationId, Long orderId) {
        VoucherReservation reservation = lockedReservation(reservationId, orderId);
        if (reservation.getState() == VoucherReservation.State.RESERVED
                && !LocalDateTime.now().isBefore(reservation.getExpiresAt())) {
            expireReservation(reservation);
        } else if (reservation.getState() == VoucherReservation.State.RESERVED) {
            UserVoucher userVoucher = userVoucherRepository.findByUserIdAndVoucherIdForUpdate(
                            reservation.getUserId(), reservation.getVoucherId())
                    .orElseThrow(() -> new IllegalStateException("Reserved wallet voucher is missing"));
            userVoucher.setStatus(UserVoucher.Status.USED);
            userVoucher.setUsedAt(LocalDateTime.now());
            reservation.setState(VoucherReservation.State.COMMITTED);
            outboxService.enqueue(reservation);
        }
        return VoucherReservationResponse.from(reservation);
    }

    @Transactional
    public VoucherReservationResponse releaseReservation(UUID reservationId, Long orderId) {
        VoucherReservation reservation = lockedReservation(reservationId, orderId);
        if (reservation.getState() == VoucherReservation.State.RESERVED
                || reservation.getState() == VoucherReservation.State.COMMITTED) {
            releaseCapacity(reservation, VoucherReservation.State.RELEASED);
        }
        return VoucherReservationResponse.from(reservation);
    }

    @Transactional
    public int expireReservations() {
        List<VoucherReservation> expired = voucherReservationRepository
                .findTop100ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        VoucherReservation.State.RESERVED, LocalDateTime.now());
        int count = 0;
        for (VoucherReservation candidate : expired) {
            VoucherReservation reservation = voucherReservationRepository
                    .findByIdForUpdate(candidate.getReservationId()).orElse(null);
            if (reservation != null && reservation.getState() == VoucherReservation.State.RESERVED
                    && !LocalDateTime.now().isBefore(reservation.getExpiresAt())) {
                expireReservation(reservation);
                count++;
            }
        }
        return count;
    }

    private VoucherReservation lockedReservation(UUID reservationId, Long orderId) {
        if (reservationId == null) throw new IllegalArgumentException("reservationId is required");
        validatePositiveId(orderId, "orderId");
        VoucherReservation reservation = voucherReservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher reservation not found"));
        if (!reservation.getOrderId().equals(orderId)) {
            throw new PromotionConflictException("reservationId is bound to another order");
        }
        return reservation;
    }

    private void expireReservation(VoucherReservation reservation) {
        releaseCapacity(reservation, VoucherReservation.State.EXPIRED);
    }

    private void releaseCapacity(VoucherReservation reservation, VoucherReservation.State state) {
        UserVoucher userVoucher = userVoucherRepository.findByUserIdAndVoucherIdForUpdate(
                        reservation.getUserId(), reservation.getVoucherId())
                .orElseThrow(() -> new IllegalStateException("Reserved wallet voucher is missing"));
        Voucher voucher = voucherRepository.findByIdForUpdate(reservation.getVoucherId())
                .orElseThrow(() -> new IllegalStateException("Reserved voucher is missing"));
        if (voucher.getUsedQuantity() <= 0) {
            throw new IllegalStateException("Voucher usage counter is inconsistent");
        }
        voucher.setUsedQuantity(voucher.getUsedQuantity() - 1);
        userVoucher.setStatus(UserVoucher.Status.SAVED);
        userVoucher.setOrderId(null);
        userVoucher.setUsedAt(null);
        reservation.setState(state);
        outboxService.enqueue(reservation);
    }

    private BigDecimal calculateDiscount(Voucher voucher, BigDecimal subtotal, BigDecimal shippingFee) {
        BigDecimal discount = switch (voucher.getRewardType()) {
            case FIXED -> voucher.getDiscountValue().min(subtotal);
            case PERCENTAGE -> subtotal.multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100)).min(subtotal);
            case FREESHIP -> shippingFee.min(voucher.getDiscountValue());
        };
        if (voucher.getMaxDiscountValue() != null) discount = discount.min(voucher.getMaxDiscountValue());
        return discount.max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void requireSameReservation(VoucherReservation reservation, ReserveRequest request) {
        if (!reservation.getReservationId().equals(request.getReservationId())
                || !reservation.getOrderId().equals(request.getOrderId())
                || !reservation.getUserId().equals(request.getUserId())
                || !reservation.getVoucherId().equals(request.getVoucherId())
                || !reservation.getRestaurantId().equals(request.getRestaurantId())
                || reservation.getSubtotal().compareTo(request.getSubtotal()) != 0
                || reservation.getShippingFee().compareTo(request.getShippingFee()) != 0) {
            throw new PromotionConflictException("Reservation replay payload does not match");
        }
    }

    @Transactional(readOnly = true)
    public List<Voucher> getCollectedVouchers(Long userId) {
        validatePositiveId(userId, "userId");
        List<UserVoucher> userVouchers = userVoucherRepository.findByUserIdAndStatus(
                userId, UserVoucher.Status.SAVED,
                PageRequest.of(0, COMPATIBILITY_LIST_LIMIT));
        List<Long> voucherIds = userVouchers.stream()
                .map(UserVoucher::getVoucherId)
                .collect(Collectors.toList());
        return voucherRepository.findAllById(voucherIds);
    }

    @Transactional(readOnly = true)
    public List<Voucher> listAllVouchers() {
        return voucherRepository.findAll(PageRequest.of(0, COMPATIBILITY_LIST_LIMIT)).getContent();
    }

    @Transactional(readOnly = true)
    public List<Voucher> listMerchantVouchers(Long merchantId) {
        validatePositiveId(merchantId, "merchantId");
        return voucherRepository.findByCreatorTypeAndCreatorId(
                Voucher.CreatorType.MERCHANT,
                merchantId,
                PageRequest.of(0, COMPATIBILITY_LIST_LIMIT));
    }

    @Transactional
    public void deleteVoucher(Long id) {
        validatePositiveId(id, "voucherId");
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
        voucher.setActive(false);
        voucherRepository.save(voucher);
    }

    private void validateCreateVoucherRequest(CreateVoucherRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Create voucher request is required");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Voucher code is required");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Voucher name is required");
        }
        if (request.getCreatorType() == null) {
            throw new IllegalArgumentException("Voucher creatorType is required");
        }
        if (request.getRewardType() == null) {
            throw new IllegalArgumentException("Voucher rewardType is required");
        }
        if (request.getScopeType() == null) {
            throw new IllegalArgumentException("Voucher scopeType is required");
        }
        if (request.getDiscountValue() == null || request.getDiscountValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Voucher discountValue must be non-negative");
        }
        if (request.getTotalQuantity() == null || request.getTotalQuantity() < 1) {
            throw new IllegalArgumentException("Voucher totalQuantity must be positive");
        }
        if (request.getUsageLimitPerUser() == null || request.getUsageLimitPerUser() < 1) {
            throw new IllegalArgumentException("Voucher usageLimitPerUser must be positive");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new IllegalArgumentException("Voucher time window is required");
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new IllegalArgumentException("Voucher startTime must be before endTime");
        }
        if (request.getMinOrderValue() == null || request.getMinOrderValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Voucher minOrderValue must be non-negative");
        }
        if (request.getCreatorType() != Voucher.CreatorType.PLATFORM) {
            throw new IllegalArgumentException("Voucher campaigns must be owned by ADMIN");
        }
        if (request.getScopeType() != Voucher.ScopeType.ALL
                && request.getScopeType() != Voucher.ScopeType.SHOP) {
            throw new IllegalArgumentException("Voucher scope must be ALL or SHOP");
        }
        if (request.getScopeType() == Voucher.ScopeType.SHOP
                && (request.getScopeRefId() == null || request.getScopeRefId() <= 0)) {
            throw new IllegalArgumentException("Voucher scopeRefId must identify a restaurant");
        }
        if (request.getScopeType() == Voucher.ScopeType.ALL && request.getScopeRefId() != null) {
            throw new IllegalArgumentException("Platform voucher must not carry scopeRefId");
        }
    }

    private void validateCalculateRequest(CartContextRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Cart context request is required");
        }
        validatePositiveId(request.getUserId(), "userId");
        validatePositiveId(request.getShopId(), "shopId");
        if (request.getSubTotal() == null || request.getSubTotal().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("subTotal must be non-negative");
        }
        if (request.getShippingFee() == null || request.getShippingFee().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("shippingFee must be non-negative");
        }
    }

    private void validateReserveRequest(ReserveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Reserve request is required");
        }
        validatePositiveId(request.getUserId(), "userId");
        validatePositiveId(request.getOrderId(), "orderId");
        if (request.getReservationId() == null) throw new IllegalArgumentException("reservationId is required");
        validatePositiveId(request.getVoucherId(), "voucherId");
        validatePositiveId(request.getRestaurantId(), "restaurantId");
        if (request.getSubtotal() == null || request.getSubtotal().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("subtotal must be non-negative");
        if (request.getShippingFee() == null || request.getShippingFee().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("shippingFee must be non-negative");
    }

    private void validatePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
