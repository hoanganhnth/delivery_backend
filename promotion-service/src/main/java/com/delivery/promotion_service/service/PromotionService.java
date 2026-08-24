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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import com.delivery.promotion_service.exception.PromotionConflictException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import com.delivery.promotion_service.dto.CreateVoucherRequest;
import com.delivery.promotion_service.dto.VoucherSelectionMode;
import com.delivery.promotion_service.dto.BulkReserveRequest;
import com.delivery.promotion_service.dto.PromotionReservationResponse;
import com.delivery.promotion_service.entity.PromotionReservation;
import com.delivery.promotion_service.entity.PromotionReservationLine;
import com.delivery.promotion_service.repository.PromotionReservationRepository;
import com.delivery.promotion_service.repository.PromotionReservationLineRepository;

@Service
@Slf4j
public class PromotionService {

    private static final int COMPATIBILITY_LIST_LIMIT = 100;

    private final VoucherRepository voucherRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final VoucherGroupRepository voucherGroupRepository;
    private final VoucherReservationRepository voucherReservationRepository;
    private final PromotionOutboxService outboxService;
    private final MeterRegistry meterRegistry;
    private final PromotionReservationRepository promotionReservationRepository;
    private final PromotionReservationLineRepository promotionReservationLineRepository;
    private final VoucherStackingCalculator stackingCalculator = new VoucherStackingCalculator();

    @Autowired(required = false)
    private RestaurantOwnershipClient restaurantOwnershipClient;

    @org.springframework.beans.factory.annotation.Value("${app.identity.principal-ownership.enforced:false}")
    private boolean principalOwnershipEnforced;

    @Autowired
    public PromotionService(VoucherRepository voucherRepository,
            UserVoucherRepository userVoucherRepository,
            VoucherGroupRepository voucherGroupRepository,
            VoucherReservationRepository voucherReservationRepository,
            PromotionOutboxService outboxService,
            MeterRegistry meterRegistry,
            PromotionReservationRepository promotionReservationRepository,
            PromotionReservationLineRepository promotionReservationLineRepository) {
        this.voucherRepository = voucherRepository;
        this.userVoucherRepository = userVoucherRepository;
        this.voucherGroupRepository = voucherGroupRepository;
        this.voucherReservationRepository = voucherReservationRepository;
        this.outboxService = outboxService;
        this.meterRegistry = meterRegistry;
        this.promotionReservationRepository = promotionReservationRepository;
        this.promotionReservationLineRepository = promotionReservationLineRepository;
    }

    /** Compatibility constructor for existing focused fixtures; production injects Prometheus registry. */
    public PromotionService(VoucherRepository voucherRepository,
            UserVoucherRepository userVoucherRepository,
            VoucherGroupRepository voucherGroupRepository,
            VoucherReservationRepository voucherReservationRepository,
            PromotionOutboxService outboxService) {
        this(voucherRepository, userVoucherRepository, voucherGroupRepository,
                voucherReservationRepository, outboxService, new SimpleMeterRegistry(), null, null);
    }

    @Transactional
    public Voucher createVoucher(CreateVoucherRequest request) {
        validateCreateVoucherRequest(request);
        request.setCode(normalizeCode(request.getCode()));
        if (voucherRepository.findByCode(request.getCode()).isPresent()) {
            throw new PromotionConflictException("Voucher code already exists");
        }
        boolean shopOwned = request.getCreatorType() == Voucher.CreatorType.SHOP;
        String layer = request.getLayerCode();
        if (layer == null || layer.isBlank()) {
            layer = request.getRewardType() == Voucher.RewardType.FREESHIP
                    ? VoucherLayer.FREESHIP.name()
                    : shopOwned ? VoucherLayer.SHOP_DISCOUNT.name() : VoucherLayer.PLATFORM_DISCOUNT.name();
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
                .layerCode(layer.toUpperCase(Locale.ROOT))
                .fundingSource(shopOwned ? "SHOP" : "PLATFORM")
                .approvalStatus(shopOwned ? "PENDING" : "APPROVED")
                .ownerPrincipalId(request.getOwnerPrincipalId())
                .restaurantId(shopOwned ? request.getRestaurantId() : request.getScopeRefId())
                .active(!shopOwned)
                .build();
        try {
            return voucherRepository.saveAndFlush(voucher);
        } catch (DataIntegrityViolationException ex) {
            throw new PromotionConflictException("Voucher code already exists", ex);
        }
    }

    /** Creates a restaurant-funded campaign after Restaurant confirms ownership. */
    @Transactional
    public Voucher createShopVoucher(CreateVoucherRequest request, Long ownerPrincipalId, Long legacyOwnerId) {
        if (request == null) throw new IllegalArgumentException("Create voucher request is required");
        validatePositiveId(ownerPrincipalId, "ownerPrincipalId");
        validatePositiveId(legacyOwnerId, "legacyOwnerId");
        validatePositiveId(request.getRestaurantId(), "restaurantId");
        if (restaurantOwnershipClient == null
                || !restaurantOwnershipClient.isOwnedBy(request.getRestaurantId(), ownerPrincipalId, legacyOwnerId)) {
            throw new PromotionConflictException("Shop owner is not authorized for this restaurant");
        }
        request.setCreatorType(Voucher.CreatorType.SHOP);
        request.setCreatorId(legacyOwnerId);
        request.setOwnerPrincipalId(ownerPrincipalId);
        request.setScopeType(Voucher.ScopeType.SHOP);
        request.setScopeRefId(request.getRestaurantId());
        request.setLayerCode(VoucherLayer.SHOP_DISCOUNT.name());
        request.setFundingSource("SHOP");
        return createVoucher(request);
    }

    @Transactional
    public void collectVoucher(Long principalId, Long userId, String voucherCode) {
        validatePositiveId(principalId, "principalId");
        validatePositiveId(userId, "userId");
        if (voucherCode == null || voucherCode.isBlank()) {
            throw new IllegalArgumentException("Voucher code is required");
        }
        Voucher voucher = voucherRepository.findByCode(normalizeCode(voucherCode))
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
        if (!isCheckoutWalletVoucher(voucher)) {
            throw new IllegalArgumentException("Voucher is not checkout-eligible");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!isApproved(voucher) || !Boolean.TRUE.equals(voucher.getActive()) || voucher.getEndTime().isBefore(now)) {
            throw new IllegalArgumentException("Voucher is expired or inactive");
        }
        if (voucher.getStartTime() != null && now.isBefore(voucher.getStartTime())) {
            throw new IllegalArgumentException("Voucher is not active yet");
        }

        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            throw new IllegalArgumentException("Voucher is out of stock");
        }

        Optional<UserVoucher> existing = principalOwnershipEnforced
                ? userVoucherRepository.findByUserPrincipalIdAndVoucherId(principalId, voucher.getId())
                : userVoucherRepository.findByPrincipalOrUnbackfilledLegacyAndVoucherId(principalId, userId, voucher.getId());
        if (existing.isPresent()) {
            if (!principalOwnershipEnforced && existing.get().getUserPrincipalId() == null) {
                legacyWalletFallback().increment();
            }
            throw new PromotionConflictException("Voucher already collected");
        }

        UserVoucher userVoucher = UserVoucher.builder()
                .userId(userId)
                .userPrincipalId(principalId)
                .voucherId(voucher.getId())
                .status(UserVoucher.Status.SAVED)
                .build();
        
        try {
            userVoucherRepository.saveAndFlush(userVoucher);
        } catch (DataIntegrityViolationException ex) {
            throw new PromotionConflictException("Voucher already collected", ex);
        }
    }

    /** Legacy compatibility rail for internal callers that do not yet carry principalId. */
    @Transactional
    public void collectVoucher(Long userId, String voucherCode) {
        validatePositiveId(userId, "userId");
        if (voucherCode == null || voucherCode.isBlank()) {
            throw new IllegalArgumentException("Voucher code is required");
        }
        Voucher voucher = voucherRepository.findByCode(normalizeCode(voucherCode))
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
        if (!isCheckoutWalletVoucher(voucher)) {
            throw new IllegalArgumentException("Voucher is not checkout-eligible");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!isApproved(voucher) || !Boolean.TRUE.equals(voucher.getActive()) || voucher.getEndTime().isBefore(now)) {
            throw new IllegalArgumentException("Voucher is expired or inactive");
        }
        if (voucher.getStartTime() != null && now.isBefore(voucher.getStartTime())) {
            throw new IllegalArgumentException("Voucher is not active yet");
        }
        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            throw new IllegalArgumentException("Voucher is out of stock");
        }
        if (userVoucherRepository.findByUserIdAndVoucherId(userId, voucher.getId()).isPresent()) {
            throw new PromotionConflictException("Voucher already collected");
        }
        try {
            userVoucherRepository.saveAndFlush(UserVoucher.builder()
                    .userId(userId).voucherId(voucher.getId()).status(UserVoucher.Status.SAVED).build());
        } catch (DataIntegrityViolationException ex) {
            throw new PromotionConflictException("Voucher already collected", ex);
        }
    }

    @Transactional(readOnly = true)
    public CalculateResponse calculate(CartContextRequest request) {
        validateCalculateRequest(request);
        List<UserVoucher> savedVouchers = walletVouchers(request.getUserPrincipalId(), request.getUserId(),
                UserVoucher.Status.SAVED);
        Map<Long, Voucher> vouchersById = voucherRepository.findAllById(savedVouchers.stream()
                        .map(UserVoucher::getVoucherId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Voucher::getId, voucher -> voucher));
        
        List<Long> selectedIds = normalizedSelectedVoucherIds(request);
        VoucherSelectionMode mode = request.getSelectionMode();
        if (mode == null) mode = selectedIds.isEmpty() ? VoucherSelectionMode.AUTO : VoucherSelectionMode.MANUAL;
        VoucherStackingCalculator.Calculation calculation = stackingCalculator.calculate(
                vouchersById.values(), request.getShopId(), request.getSubTotal(), request.getShippingFee(),
                selectedIds, mode, LocalDateTime.now());

        List<CalculateResponse.VoucherInfo> available = new ArrayList<>();
        for (Voucher voucher : vouchersById.values()) {
            if (calculation.unavailableVouchers().stream().noneMatch(item -> voucher.getId().equals(item.voucherId()))) {
                available.add(CalculateResponse.VoucherInfo.builder()
                        .id(voucher.getId()).code(voucher.getCode()).name(voucher.getName())
                        .rewardType(voucher.getRewardType()).discountValue(voucher.getDiscountValue())
                        .voucherGroupId(voucher.getVoucherGroupId()).build());
            }
        }
        List<CalculateResponse.UnavailableVoucherInfo> unavailable = calculation.unavailableVouchers().stream()
                .map(item -> {
                    Voucher voucher = vouchersById.get(item.voucherId());
                    return CalculateResponse.UnavailableVoucherInfo.builder()
                            .id(item.voucherId()).code(item.code())
                            .name(voucher == null ? null : voucher.getName()).reason(item.reason()).build();
                }).toList();
        List<CalculateResponse.AppliedVoucherInfo> applied = calculation.appliedVouchers().stream()
                .map(item -> CalculateResponse.AppliedVoucherInfo.builder()
                        .id(item.voucherId()).code(item.code()).layer(item.layer())
                        .discountAmount(item.discountAmount()).discountBase(item.discountBase())
                        .fundingSource(item.fundingSource()).build())
                .toList();
        return CalculateResponse.builder()
                .availableVouchers(available)
                .unavailableVouchers(unavailable)
                .finalSubTotal(request.getSubTotal())
                .finalShippingFee(request.getShippingFee())
                .totalDiscount(calculation.totalDiscount())
                .totalAmount(calculation.totalAmount())
                .itemDiscount(calculation.itemDiscount())
                .shippingDiscount(calculation.shippingDiscount())
                .customerShippingFee(calculation.customerShippingFee())
                .selectedVoucherIds(calculation.appliedVouchers().stream()
                        .map(VoucherStackingCalculator.AppliedVoucher::voucherId).toList())
                .appliedVouchers(applied)
                .build();
    }

    private List<Long> normalizedSelectedVoucherIds(CartContextRequest request) {
        List<Long> ids = request.getSelectedVoucherIds() == null
                ? new ArrayList<>() : new ArrayList<>(request.getSelectedVoucherIds());
        if (request.getSelectedVoucherId() != null && ids.isEmpty()) ids.add(request.getSelectedVoucherId());
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("selectedVoucherIds must contain positive IDs");
        }
        if (ids.size() > 3 || ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException("At most one voucher per layer is supported");
        }
        return ids;
    }

    /**
     * Atomically reserves all selected voucher layers for the new stacking
     * contract. The legacy reserveVoucher method below remains available for
     * old callers and old rows during the expand/contract rollout.
     */
    @Transactional
    public PromotionReservationResponse reserveVouchers(BulkReserveRequest request) {
        requireBulkRepositories();
        validateBulkReserveRequest(request);
        List<Long> voucherIds = request.getVoucherIds().stream().sorted().toList();

        Optional<PromotionReservation> sameId = promotionReservationRepository.findById(request.getReservationId());
        if (sameId.isPresent()) {
            requireSameBulkReservation(sameId.get(), request, voucherIds);
            return bulkResponse(sameId.get());
        }
        Optional<PromotionReservation> sameOrder = promotionReservationRepository.findByOrderId(request.getOrderId());
        if (sameOrder.isPresent()) {
            requireSameBulkReservation(sameOrder.get(), request, voucherIds);
            return bulkResponse(sameOrder.get());
        }

        Map<Long, UserVoucher> wallets = new LinkedHashMap<>();
        Map<Long, Voucher> vouchers = new LinkedHashMap<>();
        for (Long voucherId : voucherIds) {
            UserVoucher wallet = walletVoucherForUpdate(
                    request.getUserPrincipalId(), request.getUserId(), voucherId);
            if (wallet.getStatus() != UserVoucher.Status.SAVED) {
                throw new PromotionConflictException("Voucher is already reserved or used");
            }
            Voucher voucher = voucherRepository.findByIdForUpdate(voucherId)
                    .orElseThrow(() -> new IllegalArgumentException("Voucher not found: " + voucherId));
            ensureWalletCapacity(wallet, voucher);
            wallets.put(voucherId, wallet);
            vouchers.put(voucherId, voucher);
        }

        VoucherStackingCalculator.Calculation calculation = stackingCalculator.calculate(
                vouchers.values(), request.getRestaurantId(), request.getSubtotal(),
                request.getGrossShippingFee(), voucherIds, VoucherSelectionMode.MANUAL, LocalDateTime.now());
        Set<Long> appliedIds = calculation.appliedVouchers().stream()
                .map(VoucherStackingCalculator.AppliedVoucher::voucherId).collect(Collectors.toSet());
        if (!appliedIds.equals(new HashSet<>(voucherIds))) {
            throw new PromotionConflictException("Voucher selection is no longer eligible");
        }

        LocalDateTime now = LocalDateTime.now();
        PromotionReservation reservation = PromotionReservation.builder()
                .reservationId(request.getReservationId())
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .userPrincipalId(request.getUserPrincipalId())
                .restaurantId(request.getRestaurantId())
                .subtotal(request.getSubtotal().setScale(2, java.math.RoundingMode.HALF_UP))
                .grossShippingFee(request.getGrossShippingFee().setScale(2, java.math.RoundingMode.HALF_UP))
                .itemDiscount(calculation.itemDiscount())
                .shippingDiscount(calculation.shippingDiscount())
                .totalDiscount(calculation.totalDiscount())
                .customerShippingFee(calculation.customerShippingFee())
                .state(PromotionReservation.State.RESERVED)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .createdAt(now)
                .updatedAt(now)
                .build();

        List<PromotionReservationLine> lines = new ArrayList<>();
        Map<Long, VoucherStackingCalculator.AppliedVoucher> appliedById = calculation.appliedVouchers().stream()
                .collect(Collectors.toMap(VoucherStackingCalculator.AppliedVoucher::voucherId,
                        item -> item));
        for (Long voucherId : voucherIds) {
            VoucherStackingCalculator.AppliedVoucher applied = appliedById.get(voucherId);
            UserVoucher wallet = wallets.get(voucherId);
            Voucher voucher = vouchers.get(voucherId);
            wallet.setReservedCount(safeCount(wallet.getReservedCount()) + 1);
            wallet.setStatus(UserVoucher.Status.RESERVED);
            wallet.setOrderId(request.getOrderId());
            voucher.setUsedQuantity(safeCount(voucher.getUsedQuantity()) + 1);
            lines.add(PromotionReservationLine.builder()
                    .reservationId(reservation.getReservationId())
                    .voucherId(voucherId)
                    .voucherCode(voucher.getCode())
                    .layer(applied.layer().name())
                    .fundingSource(applied.fundingSource())
                    .discountBase(applied.discountBase())
                    .discountAmount(applied.discountAmount())
                    .state(PromotionReservationLine.State.RESERVED)
                    .build());
        }
        try {
            promotionReservationRepository.saveAndFlush(reservation);
            promotionReservationLineRepository.saveAll(lines);
            promotionReservationLineRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new PromotionConflictException("Conflicting promotion reservation", ex);
        }
        outboxService.enqueue(reservation, lines);
        return PromotionReservationResponse.from(reservation, lines);
    }

    @Transactional
    public PromotionReservationResponse commitPromotionReservation(UUID reservationId, Long orderId) {
        return commitPromotionReservationInternal(reservationId, orderId, null, false);
    }

    /** Public HTTP rail supplies the stable principal; Kafka recovery uses the trusted two-argument path. */
    @Transactional
    public PromotionReservationResponse commitPromotionReservation(UUID reservationId, Long orderId,
                                                                    Long userPrincipalId) {
        return commitPromotionReservationInternal(reservationId, orderId, userPrincipalId, true);
    }

    private PromotionReservationResponse commitPromotionReservationInternal(UUID reservationId, Long orderId,
                                                                              Long userPrincipalId,
                                                                              boolean enforcePrincipal) {
        requireBulkRepositories();
        PromotionReservation reservation = lockedBulkReservation(reservationId, orderId);
        if (enforcePrincipal) requireBulkReservationPrincipal(reservation, userPrincipalId);
        List<PromotionReservationLine> lines = promotionReservationLineRepository
                .findByReservationIdForUpdateOrderByVoucherIdAsc(reservationId);
        if (reservation.getState() == PromotionReservation.State.RESERVED
                && !LocalDateTime.now().isBefore(reservation.getExpiresAt())) {
            // Expiry is owned by the scheduled recovery job. A late order.created
            // must never look successful: otherwise Order keeps the discount while
            // the expiry path has already returned the quota to the wallet.
            throw new PromotionConflictException("Promotion reservation expired before commit");
        } else if (reservation.getState() == PromotionReservation.State.RESERVED) {
            transitionBulkReservation(reservation, lines, PromotionReservation.State.COMMITTED);
        } else if (reservation.getState() != PromotionReservation.State.COMMITTED) {
            throw new PromotionConflictException(
                    "Promotion reservation cannot be committed from state " + reservation.getState());
        }
        return PromotionReservationResponse.from(reservation, lines);
    }

    @Transactional
    public PromotionReservationResponse releasePromotionReservation(UUID reservationId, Long orderId) {
        return releasePromotionReservationInternal(reservationId, orderId, null, false);
    }

    /** Public HTTP rail supplies the stable principal; Kafka recovery uses the trusted two-argument path. */
    @Transactional
    public PromotionReservationResponse releasePromotionReservation(UUID reservationId, Long orderId,
                                                                      Long userPrincipalId) {
        return releasePromotionReservationInternal(reservationId, orderId, userPrincipalId, true);
    }

    private PromotionReservationResponse releasePromotionReservationInternal(UUID reservationId, Long orderId,
                                                                               Long userPrincipalId,
                                                                               boolean enforcePrincipal) {
        requireBulkRepositories();
        PromotionReservation reservation = lockedBulkReservation(reservationId, orderId);
        if (enforcePrincipal) requireBulkReservationPrincipal(reservation, userPrincipalId);
        List<PromotionReservationLine> lines = promotionReservationLineRepository
                .findByReservationIdForUpdateOrderByVoucherIdAsc(reservationId);
        if (reservation.getState() == PromotionReservation.State.RESERVED
                || reservation.getState() == PromotionReservation.State.COMMITTED) {
            transitionBulkReservation(reservation, lines, PromotionReservation.State.RELEASED);
        }
        return PromotionReservationResponse.from(reservation, lines);
    }

    private void requireBulkReservationPrincipal(PromotionReservation reservation, Long userPrincipalId) {
        validatePositiveId(userPrincipalId, "userPrincipalId");
        if (reservation.getUserPrincipalId() == null
                || !reservation.getUserPrincipalId().equals(userPrincipalId)) {
            throw new PromotionConflictException("Promotion reservation is owned by another principal");
        }
    }

    @Transactional
    public int expirePromotionReservations() {
        requireBulkRepositories();
        List<PromotionReservation> candidates = promotionReservationRepository
                .findTop100ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        PromotionReservation.State.RESERVED, LocalDateTime.now());
        int count = 0;
        for (PromotionReservation candidate : candidates) {
            PromotionReservation reservation = promotionReservationRepository
                    .findByIdForUpdate(candidate.getReservationId()).orElse(null);
            if (reservation != null && reservation.getState() == PromotionReservation.State.RESERVED
                    && !LocalDateTime.now().isBefore(reservation.getExpiresAt())) {
                List<PromotionReservationLine> lines = promotionReservationLineRepository
                        .findByReservationIdForUpdateOrderByVoucherIdAsc(reservation.getReservationId());
                transitionBulkReservation(reservation, lines, PromotionReservation.State.EXPIRED);
                count++;
            }
        }
        return count;
    }

    private void transitionBulkReservation(PromotionReservation reservation,
                                            List<PromotionReservationLine> lines,
                                            PromotionReservation.State target) {
        boolean wasCommitted = reservation.getState() == PromotionReservation.State.COMMITTED;
        for (PromotionReservationLine line : lines) {
            UserVoucher wallet = walletVoucherForUpdate(reservation.getUserPrincipalId(), reservation.getUserId(),
                    line.getVoucherId());
            Voucher voucher = voucherRepository.findByIdForUpdate(line.getVoucherId())
                    .orElseThrow(() -> new IllegalStateException("Reserved voucher is missing: " + line.getVoucherId()));
            int global = safeCount(voucher.getUsedQuantity());
            if (target == PromotionReservation.State.COMMITTED) {
                wallet.setReservedCount(Math.max(0, safeCount(wallet.getReservedCount()) - 1));
                wallet.setUsedCount(safeCount(wallet.getUsedCount()) + 1);
            } else {
                if (global <= 0) throw new IllegalStateException("Voucher usage counter is inconsistent");
                voucher.setUsedQuantity(global - 1);
                if (wasCommitted) {
                    wallet.setUsedCount(Math.max(0, safeCount(wallet.getUsedCount()) - 1));
                } else {
                    wallet.setReservedCount(Math.max(0, safeCount(wallet.getReservedCount()) - 1));
                }
            }
            updateWalletCompatibilityState(wallet, voucher, reservation.getOrderId());
            line.setState(PromotionReservationLine.State.valueOf(target.name()));
        }
        reservation.setState(target);
        outboxService.enqueue(reservation, lines);
    }

    private PromotionReservation lockedBulkReservation(UUID reservationId, Long orderId) {
        if (reservationId == null) throw new IllegalArgumentException("reservationId is required");
        validatePositiveId(orderId, "orderId");
        PromotionReservation reservation = promotionReservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Promotion reservation not found"));
        if (!orderId.equals(reservation.getOrderId())) {
            throw new PromotionConflictException("reservationId is bound to another order");
        }
        return reservation;
    }

    private PromotionReservationResponse bulkResponse(PromotionReservation reservation) {
        return PromotionReservationResponse.from(reservation,
                promotionReservationLineRepository.findByReservationIdOrderByVoucherIdAsc(
                        reservation.getReservationId()));
    }

    private void requireSameBulkReservation(PromotionReservation reservation, BulkReserveRequest request,
                                            List<Long> voucherIds) {
        List<Long> existing = promotionReservationLineRepository.findByReservationIdOrderByVoucherIdAsc(
                        reservation.getReservationId()).stream()
                .map(PromotionReservationLine::getVoucherId).sorted().toList();
        if (!reservation.getOrderId().equals(request.getOrderId())
                || !reservation.getUserId().equals(request.getUserId())
                || !Objects.equals(reservation.getUserPrincipalId(), request.getUserPrincipalId())
                || !reservation.getRestaurantId().equals(request.getRestaurantId())
                || reservation.getSubtotal().compareTo(request.getSubtotal()) != 0
                || reservation.getGrossShippingFee().compareTo(request.getGrossShippingFee()) != 0
                || !existing.equals(voucherIds)) {
            throw new PromotionConflictException("Promotion reservation replay payload does not match");
        }
    }

    private void validateBulkReserveRequest(BulkReserveRequest request) {
        if (request == null) throw new IllegalArgumentException("Promotion reserve request is required");
        validatePositiveId(request.getUserId(), "userId");
        if (principalOwnershipEnforced) validatePositiveId(request.getUserPrincipalId(), "userPrincipalId");
        validatePositiveId(request.getOrderId(), "orderId");
        validatePositiveId(request.getRestaurantId(), "restaurantId");
        if (request.getReservationId() == null) throw new IllegalArgumentException("reservationId is required");
        if (request.getSubtotal() == null || request.getSubtotal().signum() < 0)
            throw new IllegalArgumentException("subtotal must be non-negative");
        if (request.getGrossShippingFee() == null || request.getGrossShippingFee().signum() < 0)
            throw new IllegalArgumentException("grossShippingFee must be non-negative");
        if (request.getVoucherIds() == null || request.getVoucherIds().isEmpty()
                || request.getVoucherIds().size() > 3
                || request.getVoucherIds().stream().anyMatch(id -> id == null || id <= 0)
                || request.getVoucherIds().stream().distinct().count() != request.getVoucherIds().size()) {
            throw new IllegalArgumentException("Bulk reserve requires one to three distinct voucher IDs");
        }
    }

    private void ensureWalletCapacity(UserVoucher wallet, Voucher voucher) {
        int used = safeCount(wallet.getUsedCount());
        int reserved = safeCount(wallet.getReservedCount());
        int limit = voucher.getUsageLimitPerUser() == null ? 1 : voucher.getUsageLimitPerUser();
        if (used + reserved >= limit) {
            throw new PromotionConflictException("Voucher usage limit has been reached");
        }
    }

    private void updateWalletCompatibilityState(UserVoucher wallet, Voucher voucher, Long orderId) {
        int used = safeCount(wallet.getUsedCount());
        int reserved = safeCount(wallet.getReservedCount());
        int limit = voucher.getUsageLimitPerUser() == null ? 1 : voucher.getUsageLimitPerUser();
        if (reserved > 0) wallet.setStatus(UserVoucher.Status.RESERVED);
        else if (used >= limit) wallet.setStatus(UserVoucher.Status.USED);
        else wallet.setStatus(UserVoucher.Status.SAVED);
        wallet.setOrderId(reserved > 0 ? orderId : null);
        wallet.setUsedAt(used > 0 ? LocalDateTime.now() : null);
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private void requireBulkRepositories() {
        if (promotionReservationRepository == null || promotionReservationLineRepository == null) {
            throw new IllegalStateException("Promotion stacking persistence is unavailable");
        }
    }

    private String checkVoucherAvailability(Voucher voucher, CartContextRequest request) {
        if (!isCheckoutWalletVoucher(voucher)) return "Voucher is not checkout-eligible";
        if (!isApproved(voucher)) return "Voucher is not approved";
        if (!Boolean.TRUE.equals(voucher.getActive())) return "Voucher is inactive";
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartTime() != null && now.isBefore(voucher.getStartTime())) return "Voucher is not active yet";
        if (voucher.getEndTime() == null || !now.isBefore(voucher.getEndTime())) return "Voucher expired";
        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) return "Out of stock";
        BigDecimal minOrderValue = voucher.getMinOrderValue() == null ? BigDecimal.ZERO : voucher.getMinOrderValue();
        if (request.getSubTotal().compareTo(minOrderValue) < 0) {
            return "Need " + minOrderValue.subtract(request.getSubTotal()) + " more to use";
        }
        if (voucher.getScopeType() == Voucher.ScopeType.SHOP
                && (voucher.getScopeRefId() == null || !voucher.getScopeRefId().equals(request.getShopId()))) {
            return "Not applicable for this shop";
        }
        if (voucher.getScopeType() == Voucher.ScopeType.CATEGORY) {
            return "Legacy CATEGORY voucher is not checkout-eligible";
        }
        try {
            VoucherLayer layer = VoucherLayerResolver.resolve(voucher);
            if (layer == VoucherLayer.FREESHIP && voucher.getRewardType() != Voucher.RewardType.FREESHIP) {
                return "Freeship layer requires a freeship reward";
            }
            if (layer != VoucherLayer.FREESHIP && voucher.getRewardType() == Voucher.RewardType.FREESHIP) {
                return "Freeship reward cannot be used as an item discount";
            }
        } catch (IllegalArgumentException invalidLayer) {
            return invalidLayer.getMessage() == null ? "Voucher layer is invalid" : invalidLayer.getMessage();
        }
        return null; // Available
    }

    @Transactional
    public VoucherReservationResponse reserveVoucher(ReserveRequest request) {
        validateReserveRequest(request);
        Optional<VoucherReservation> sameId = voucherReservationRepository.findById(request.getReservationId());
        if (sameId.isPresent()) {
            requireSameReservation(sameId.get(), request);
            return legacyReservationResponse(sameId.get());
        }
        Optional<VoucherReservation> sameOrder = voucherReservationRepository.findByOrderId(request.getOrderId());
        if (sameOrder.isPresent()) {
            requireSameReservation(sameOrder.get(), request);
            return legacyReservationResponse(sameOrder.get());
        }

        UserVoucher userVoucher = walletVoucherForUpdate(
                request.getUserPrincipalId(), request.getUserId(), request.getVoucherId());
        if (userVoucher.getStatus() != UserVoucher.Status.SAVED) {
            throw new PromotionConflictException("Voucher is already reserved or used");
        }

        Voucher voucher = voucherRepository.findByIdForUpdate(request.getVoucherId())
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
        String unavailable = checkVoucherAvailability(voucher,
                CartContextRequest.builder()
                        .userId(request.getUserId())
                        .userPrincipalId(request.getUserPrincipalId())
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
                .userPrincipalId(request.getUserPrincipalId())
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
        return legacyReservationResponse(reservation, voucher);
    }

    @Transactional
    public VoucherReservationResponse commitReservation(UUID reservationId, Long orderId) {
        VoucherReservation reservation = lockedReservation(reservationId, orderId);
        if (reservation.getState() == VoucherReservation.State.RESERVED
                && !LocalDateTime.now().isBefore(reservation.getExpiresAt())) {
            // Do not silently ACK a late order.created event. The expiry job is
            // the only owner of RESERVED -> EXPIRED capacity restoration.
            throw new PromotionConflictException("Voucher reservation expired before commit");
        } else if (reservation.getState() == VoucherReservation.State.RESERVED) {
            UserVoucher userVoucher = walletVoucherForUpdate(reservation.getUserPrincipalId(), reservation.getUserId(),
                    reservation.getVoucherId());
            userVoucher.setStatus(UserVoucher.Status.USED);
            userVoucher.setUsedAt(LocalDateTime.now());
            reservation.setState(VoucherReservation.State.COMMITTED);
            outboxService.enqueue(reservation);
        } else if (reservation.getState() != VoucherReservation.State.COMMITTED) {
            throw new PromotionConflictException(
                    "Voucher reservation cannot be committed from state " + reservation.getState());
        }
        return legacyReservationResponse(reservation);
    }

    @Transactional
    public VoucherReservationResponse releaseReservation(UUID reservationId, Long orderId) {
        VoucherReservation reservation = lockedReservation(reservationId, orderId);
        if (reservation.getState() == VoucherReservation.State.RESERVED
                || reservation.getState() == VoucherReservation.State.COMMITTED) {
            releaseCapacity(reservation, VoucherReservation.State.RELEASED);
        }
        return legacyReservationResponse(reservation);
    }

    private VoucherReservationResponse legacyReservationResponse(VoucherReservation reservation) {
        Voucher voucher = voucherRepository.findById(reservation.getVoucherId()).orElse(null);
        return legacyReservationResponse(reservation, voucher);
    }

    private VoucherReservationResponse legacyReservationResponse(VoucherReservation reservation, Voucher voucher) {
        return VoucherReservationResponse.from(reservation, voucher);
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
        UserVoucher userVoucher = walletVoucherForUpdate(reservation.getUserPrincipalId(), reservation.getUserId(),
                reservation.getVoucherId());
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
                || !java.util.Objects.equals(reservation.getUserPrincipalId(), request.getUserPrincipalId())
                || !reservation.getVoucherId().equals(request.getVoucherId())
                || !reservation.getRestaurantId().equals(request.getRestaurantId())
                || reservation.getSubtotal().compareTo(request.getSubtotal()) != 0
                || reservation.getShippingFee().compareTo(request.getShippingFee()) != 0) {
            throw new PromotionConflictException("Reservation replay payload does not match");
        }
    }

    @Transactional(readOnly = true)
    public List<Voucher> getCollectedVouchers(Long principalId, Long userId) {
        validatePositiveId(principalId, "principalId");
        validatePositiveId(userId, "userId");
        List<UserVoucher> userVouchers = walletVouchers(principalId, userId, UserVoucher.Status.SAVED);
        List<Long> voucherIds = userVouchers.stream()
                .map(UserVoucher::getVoucherId)
                .collect(Collectors.toList());
        return voucherRepository.findAllById(voucherIds).stream()
                .filter(this::isCheckoutWalletVoucher)
                .toList();
    }

    /** Legacy compatibility rail for callers compiled before the principal claim. */
    @Transactional(readOnly = true)
    public List<Voucher> getCollectedVouchers(Long userId) {
        validatePositiveId(userId, "userId");
        List<UserVoucher> userVouchers = userVoucherRepository.findByUserIdAndStatus(
                userId, UserVoucher.Status.SAVED, PageRequest.of(0, COMPATIBILITY_LIST_LIMIT));
        return voucherRepository.findAllById(userVouchers.stream()
                        .map(UserVoucher::getVoucherId).collect(Collectors.toList())).stream()
                .filter(this::isCheckoutWalletVoucher)
                .toList();
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

    @Transactional(readOnly = true)
    public List<Voucher> listShopVouchers(Long ownerPrincipalId, Long legacyOwnerId) {
        validatePositiveId(ownerPrincipalId, "ownerPrincipalId");
        validatePositiveId(legacyOwnerId, "legacyOwnerId");
        return voucherRepository.findByOwnerPrincipalOrLegacy(Voucher.CreatorType.SHOP,
                ownerPrincipalId, legacyOwnerId, PageRequest.of(0, COMPATIBILITY_LIST_LIMIT));
    }

    @Transactional(readOnly = true)
    public List<Voucher> listPendingShopVouchers() {
        return voucherRepository.findByApprovalStatusOrderByCreatedAtAsc("PENDING",
                PageRequest.of(0, COMPATIBILITY_LIST_LIMIT));
    }

    @Transactional
    public Voucher approveShopVoucher(Long voucherId, Long adminPrincipalId) {
        validatePositiveId(voucherId, "voucherId");
        validatePositiveId(adminPrincipalId, "adminPrincipalId");
        Voucher voucher = voucherRepository.findByIdForUpdate(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
        if (voucher.getCreatorType() != Voucher.CreatorType.SHOP) {
            throw new IllegalArgumentException("Only shop vouchers require approval");
        }
        if (!"PENDING".equalsIgnoreCase(voucher.getApprovalStatus())) {
            throw new PromotionConflictException("Voucher is not pending approval");
        }
        voucher.setApprovalStatus("APPROVED");
        voucher.setApprovedByPrincipalId(adminPrincipalId);
        voucher.setApprovedAt(LocalDateTime.now());
        voucher.setRejectionReason(null);
        voucher.setActive(true);
        return voucherRepository.save(voucher);
    }

    @Transactional
    public Voucher rejectShopVoucher(Long voucherId, Long adminPrincipalId, String reason) {
        validatePositiveId(voucherId, "voucherId");
        validatePositiveId(adminPrincipalId, "adminPrincipalId");
        Voucher voucher = voucherRepository.findByIdForUpdate(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
        if (voucher.getCreatorType() != Voucher.CreatorType.SHOP) {
            throw new IllegalArgumentException("Only shop vouchers require approval");
        }
        if (!"PENDING".equalsIgnoreCase(voucher.getApprovalStatus())) {
            throw new PromotionConflictException("Voucher is not pending approval");
        }
        voucher.setApprovalStatus("REJECTED");
        voucher.setApprovedByPrincipalId(adminPrincipalId);
        voucher.setApprovedAt(LocalDateTime.now());
        voucher.setRejectionReason(reason == null || reason.isBlank() ? "Rejected by admin" : reason.trim());
        voucher.setActive(false);
        return voucherRepository.save(voucher);
    }

    @Transactional
    public Voucher setVoucherActive(Long voucherId, boolean active) {
        validatePositiveId(voucherId, "voucherId");
        Voucher voucher = voucherRepository.findByIdForUpdate(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
        if (active && !isApproved(voucher)) throw new PromotionConflictException("Voucher is not approved");
        voucher.setActive(active);
        return voucherRepository.save(voucher);
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
        if (request.getMaxDiscountValue() != null
                && request.getMaxDiscountValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Voucher maxDiscountValue must be non-negative");
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
        if (request.getCreatorType() != Voucher.CreatorType.PLATFORM
                && request.getCreatorType() != Voucher.CreatorType.SHOP) {
            throw new IllegalArgumentException("Voucher campaigns must be platform or shop owned");
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
        if (request.getCreatorType() == Voucher.CreatorType.SHOP) {
            if (request.getRestaurantId() == null || request.getRestaurantId() <= 0
                    || request.getOwnerPrincipalId() == null || request.getOwnerPrincipalId() <= 0) {
                throw new IllegalArgumentException("Shop voucher requires restaurantId and ownerPrincipalId");
            }
            if (request.getRewardType() == Voucher.RewardType.FREESHIP) {
                throw new IllegalArgumentException("Shop vouchers cannot fund freeship");
            }
            if (request.getScopeType() != Voucher.ScopeType.SHOP
                    || !request.getRestaurantId().equals(request.getScopeRefId())) {
                throw new IllegalArgumentException("Shop voucher must target its restaurant");
            }
        } else if (request.getRewardType() == Voucher.RewardType.FREESHIP
                && request.getScopeType() != Voucher.ScopeType.ALL) {
            throw new IllegalArgumentException("Freeship voucher must be platform-wide");
        }

        if (request.getLayerCode() != null && !request.getLayerCode().isBlank()) {
            VoucherLayer requestedLayer;
            try {
                requestedLayer = VoucherLayer.valueOf(request.getLayerCode().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalidLayer) {
                throw new IllegalArgumentException("Voucher layer is invalid", invalidLayer);
            }
            if (request.getRewardType() == Voucher.RewardType.FREESHIP
                    && requestedLayer != VoucherLayer.FREESHIP) {
                throw new IllegalArgumentException("Freeship reward requires the FREESHIP layer");
            }
            if (request.getRewardType() != Voucher.RewardType.FREESHIP
                    && requestedLayer == VoucherLayer.FREESHIP) {
                throw new IllegalArgumentException("FREESHIP layer requires a freeship reward");
            }
            if (request.getCreatorType() == Voucher.CreatorType.SHOP
                    && requestedLayer != VoucherLayer.SHOP_DISCOUNT) {
                throw new IllegalArgumentException("Shop voucher requires the SHOP_DISCOUNT layer");
            }
            if (request.getCreatorType() == Voucher.CreatorType.PLATFORM
                    && requestedLayer == VoucherLayer.SHOP_DISCOUNT) {
                throw new IllegalArgumentException("Platform voucher cannot use the SHOP_DISCOUNT layer");
            }
        }
    }

    private void validateCalculateRequest(CartContextRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Cart context request is required");
        }
        validatePositiveId(request.getUserId(), "userId");
        if (principalOwnershipEnforced) validatePositiveId(request.getUserPrincipalId(), "userPrincipalId");
        validatePositiveId(request.getShopId(), "shopId");
        if (request.getSubTotal() == null || request.getSubTotal().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("subTotal must be non-negative");
        }
        if (request.getShippingFee() == null || request.getShippingFee().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("shippingFee must be non-negative");
        }
        normalizedSelectedVoucherIds(request);
    }

    private void validateReserveRequest(ReserveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Reserve request is required");
        }
        validatePositiveId(request.getUserId(), "userId");
        if (principalOwnershipEnforced) validatePositiveId(request.getUserPrincipalId(), "userPrincipalId");
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

    private List<UserVoucher> walletVouchers(Long principalId, Long legacyUserId, UserVoucher.Status status) {
        validatePositiveId(legacyUserId, "userId");
        if (principalOwnershipEnforced) {
            validatePositiveId(principalId, "userPrincipalId");
            return userVoucherRepository.findByUserPrincipalIdAndStatus(
                    principalId, status, PageRequest.of(0, COMPATIBILITY_LIST_LIMIT));
        }
        if (principalId == null) {
            return userVoucherRepository.findByUserIdAndStatus(legacyUserId, status,
                    PageRequest.of(0, COMPATIBILITY_LIST_LIMIT));
        }
        validatePositiveId(principalId, "userPrincipalId");
        List<UserVoucher> wallets = userVoucherRepository.findByPrincipalOrUnbackfilledLegacyAndStatus(
                principalId, legacyUserId, status, PageRequest.of(0, COMPATIBILITY_LIST_LIMIT));
        long legacyRows = wallets.stream().filter(wallet -> wallet.getUserPrincipalId() == null).count();
        if (legacyRows > 0) {
            legacyWalletFallback().increment(legacyRows);
        }
        return wallets;
    }

    private UserVoucher walletVoucherForUpdate(Long principalId, Long legacyUserId, Long voucherId) {
        validatePositiveId(legacyUserId, "userId");
        if (principalOwnershipEnforced) {
            validatePositiveId(principalId, "userPrincipalId");
            return userVoucherRepository.findByUserPrincipalIdAndVoucherIdForUpdate(principalId, voucherId)
                    .orElseThrow(() -> new IllegalArgumentException("User has not collected voucher " + voucherId));
        }
        Optional<UserVoucher> result = principalId == null
                ? userVoucherRepository.findByUserIdAndVoucherIdForUpdate(legacyUserId, voucherId)
                : userVoucherRepository.findByPrincipalOrUnbackfilledLegacyAndVoucherIdForUpdate(
                        principalId, legacyUserId, voucherId);
        UserVoucher wallet = result.orElseThrow(() -> new IllegalArgumentException("User has not collected voucher " + voucherId));
        if (principalId != null && wallet.getUserPrincipalId() == null) {
            legacyWalletFallback().increment();
            // Safe lazy backfill: selection already proves principal and legacy
            // profile ownership jointly, and the row is pessimistically locked.
            wallet.setUserPrincipalId(principalId);
        }
        return wallet;
    }

    private Counter legacyWalletFallback() {
        return Counter.builder("delivery.identity.legacy.fallback")
                .tag("service", "promotion").tag("surface", "user_voucher")
                .register(meterRegistry);
    }

    private boolean isApproved(Voucher voucher) {
        return voucher != null && (voucher.getApprovalStatus() == null
                || "APPROVED".equalsIgnoreCase(voucher.getApprovalStatus()));
    }

    private boolean isCheckoutWalletVoucher(Voucher voucher) {
        if (voucher == null || (voucher.getCreatorType() != Voucher.CreatorType.PLATFORM
                && voucher.getCreatorType() != Voucher.CreatorType.SHOP)) return false;
        if (voucher.getScopeType() != Voucher.ScopeType.ALL
                && voucher.getScopeType() != Voucher.ScopeType.SHOP) return false;
        if ((voucher.getScopeType() == Voucher.ScopeType.ALL && voucher.getScopeRefId() != null)
                || (voucher.getScopeType() == Voucher.ScopeType.SHOP && voucher.getScopeRefId() == null)) return false;
        // A wallet voucher must have a finite validity window. Besides being
        // ineligible for checkout, this guards the collect rail from calling
        // isBefore on a malformed legacy row with a null end time.
        if (voucher.getEndTime() == null) return false;
        if (voucher.getDiscountValue() == null || voucher.getDiscountValue().signum() < 0) return false;
        if (voucher.getMinOrderValue() != null && voucher.getMinOrderValue().signum() < 0) return false;
        if (voucher.getMaxDiscountValue() != null && voucher.getMaxDiscountValue().signum() < 0) return false;
        if (voucher.getTotalQuantity() == null || voucher.getTotalQuantity() < 1
                || voucher.getUsedQuantity() == null || voucher.getUsedQuantity() < 0) return false;
        try {
            VoucherLayer layer = VoucherLayerResolver.resolve(voucher);
            if (voucher.getCreatorType() == Voucher.CreatorType.SHOP
                    && (layer != VoucherLayer.SHOP_DISCOUNT
                    || voucher.getScopeType() != Voucher.ScopeType.SHOP)) return false;
            if (voucher.getCreatorType() == Voucher.CreatorType.PLATFORM
                    && layer == VoucherLayer.SHOP_DISCOUNT) return false;
            if (layer == VoucherLayer.FREESHIP) {
                return voucher.getRewardType() == Voucher.RewardType.FREESHIP
                        && voucher.getScopeType() == Voucher.ScopeType.ALL;
            }
            return voucher.getRewardType() != Voucher.RewardType.FREESHIP;
        } catch (IllegalArgumentException invalidLayer) {
            return false;
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Voucher code is required");
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
