package com.delivery.promotion_service.service;

import com.delivery.promotion.application.CalculateVouchersUseCase;
import com.delivery.promotion.application.CollectVoucherUseCase;
import com.delivery.promotion.application.ReserveVouchersUseCase;
import com.delivery.promotion.application.ReservationTransitionUseCase;
import com.delivery.promotion.application.api.CollectionPort;
import com.delivery.promotion.application.api.PricingPort;
import com.delivery.promotion.application.api.PromotionCommands;
import com.delivery.promotion.application.api.ReservationPort;
import com.delivery.promotion.application.api.ReservationTransitionPort;
import com.delivery.promotion.domain.ReservationPolicy;
import com.delivery.promotion.domain.ReservationReplayPolicy;

import com.delivery.promotion.domain.CampaignPolicy;
import com.delivery.promotion.domain.WalletClaimPolicy;
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
    private final ReservationTransitionUseCase transitions = new ReservationTransitionUseCase();

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
        var campaign = CampaignPolicy.create(VoucherDomainMapper.campaign(request));
        if (campaign.failure() != null) throw VoucherDomainMapper.creationFailure(campaign.failure());
        request.setCode(normalizeCode(request.getCode()));
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
                .layerCode(campaign.layer())
                .fundingSource(campaign.fundingSource())
                .approvalStatus(campaign.approvalStatus())
                .ownerPrincipalId(request.getOwnerPrincipalId())
                .restaurantId(campaign.restaurantId())
                .active(campaign.active())
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
        Voucher voucher = createVoucher(request);
        // Only the ownership-verified shop rail may automatically approve.
        // Historical pending/rejected rows are deliberately not migrated.
        var approval = com.delivery.promotion.domain.VoucherLifecyclePolicy.approveNewOwnedShop();
        voucher.setApprovalStatus(approval.approvalStatus());
        voucher.setApprovedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        voucher.setActive(approval.active());
        return voucherRepository.saveAndFlush(voucher);
    }

    @Transactional
    public void collectVoucher(Long principalId, Long userId, String voucherCode) {
        new CollectVoucherUseCase().collect(new CollectionPort<Voucher>() {
            private Optional<UserVoucher> existing;
            public void validateIdentity() {
                validatePositiveId(principalId, "principalId");
                validatePositiveId(userId, "userId");
            }
            public Voucher findVoucher() {
                if (voucherCode == null || voucherCode.isBlank()) throw new IllegalArgumentException("Voucher code is required");
                return voucherRepository.findByCode(normalizeCode(voucherCode))
                        .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
            }
            public com.delivery.promotion.domain.Voucher snapshot(Voucher voucher) { return VoucherDomainMapper.snapshot(voucher); }
            public LocalDateTime now() { return LocalDateTime.now(); }
            public boolean alreadyCollected(Voucher voucher) {
                existing = principalOwnershipEnforced
                        ? userVoucherRepository.findByUserPrincipalIdAndVoucherId(principalId, voucher.getId())
                        : userVoucherRepository.findByPrincipalOrUnbackfilledLegacyAndVoucherId(principalId, userId, voucher.getId());
                return existing.isPresent();
            }
            public RuntimeException duplicate(Voucher voucher, String message) {
                if (!principalOwnershipEnforced && existing.get().getUserPrincipalId() == null) {
                    legacyWalletFallback().increment();
                }
                return new PromotionConflictException(message);
            }
            public void save(Voucher voucher) {
                try {
                    userVoucherRepository.saveAndFlush(UserVoucher.builder().userId(userId).userPrincipalId(principalId)
                            .voucherId(voucher.getId()).status(UserVoucher.Status.SAVED).build());
                } catch (DataIntegrityViolationException ex) {
                    throw new PromotionConflictException("Voucher already collected", ex);
                }
            }
        });
    }

    /** Legacy compatibility rail for internal callers that do not yet carry principalId. */
    @Transactional
    public void collectVoucher(Long userId, String voucherCode) {
        new CollectVoucherUseCase().collect(new CollectionPort<Voucher>() {
            private Optional<UserVoucher> existing;
            public void validateIdentity() { validatePositiveId(userId, "userId"); }
            public Voucher findVoucher() {
                if (voucherCode == null || voucherCode.isBlank()) throw new IllegalArgumentException("Voucher code is required");
                return voucherRepository.findByCode(normalizeCode(voucherCode))
                        .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
            }
            public com.delivery.promotion.domain.Voucher snapshot(Voucher voucher) { return VoucherDomainMapper.snapshot(voucher); }
            public LocalDateTime now() { return LocalDateTime.now(); }
            public boolean alreadyCollected(Voucher voucher) {
                existing = userVoucherRepository.findByUserIdAndVoucherId(userId, voucher.getId());
                return existing.isPresent();
            }
            public RuntimeException duplicate(Voucher voucher, String message) {

                return new PromotionConflictException(message);
            }
            public void save(Voucher voucher) {
                try {
                    userVoucherRepository.saveAndFlush(UserVoucher.builder().userId(userId)
                            .voucherId(voucher.getId()).status(UserVoucher.Status.SAVED).build());
                } catch (DataIntegrityViolationException ex) {
                    throw new PromotionConflictException("Voucher already collected", ex);
                }
            }
        });
    }

    @Transactional(readOnly = true)
    public CalculateResponse calculate(CartContextRequest request) {
        return new CalculateVouchersUseCase().calculate(new PricingPort<Map<Long, Voucher>, CalculateResponse>() {
            public void validate() { validateCalculateRequest(request); }
            public Map<Long, Voucher> loadWalletVouchers() {
                List<UserVoucher> savedVouchers = walletVouchers(request.getUserPrincipalId(), request.getUserId(),
                        UserVoucher.Status.SAVED);
                Map<Long, Voucher> vouchersById = voucherRepository.findAllById(savedVouchers.stream()
                                .map(UserVoucher::getVoucherId)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Voucher::getId, voucher -> voucher));

                return vouchersById;
            }
            public PromotionCommands.Pricing pricing(Map<Long, Voucher> vouchersById) {
                List<Long> selectedIds = normalizedSelectedVoucherIds(request);
                VoucherSelectionMode mode = request.getSelectionMode();
                if (mode == null) mode = selectedIds.isEmpty() ? VoucherSelectionMode.AUTO : VoucherSelectionMode.MANUAL;

                LocalDateTime now = LocalDateTime.now();
                return new PromotionCommands.Pricing(vouchersById.values().stream().map(VoucherDomainMapper::snapshot).toList(),
                        request.getShopId(), request.getSubTotal(), request.getShippingFee(), selectedIds,
                        com.delivery.promotion.domain.VoucherSelectionMode.valueOf(mode.name()), now);
            }
            public CalculateResponse result(Map<Long, Voucher> vouchersById,
                    com.delivery.promotion.domain.VoucherStackingCalculator.Calculation quote) {
                VoucherStackingCalculator.Calculation calculation = VoucherStackingCalculator.from(quote);
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
        });
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
        return new ReserveVouchersUseCase().reserve(new ReservationPort<PromotionReservation, PromotionReservationResponse, UserVoucher, Voucher, VoucherStackingCalculator.Calculation>() {
            public PromotionCommands.Reserve prepare() {
                requireBulkRepositories();
                validateBulkReserveRequest(request);
                return new PromotionCommands.Reserve(request.getReservationId(), request.getOrderId(), request.getVoucherIds().stream().sorted().toList());
            }
            public Optional<PromotionReservation> findById() { return promotionReservationRepository.findById(request.getReservationId()); }
            public Optional<PromotionReservation> findByOrder() { return promotionReservationRepository.findByOrderId(request.getOrderId()); }
            public void requireExactReplay(PromotionReservation reservation, PromotionCommands.Reserve command) { requireSameBulkReservation(reservation, request, command.voucherIds()); }
            public PromotionReservationResponse replayResult(PromotionReservation reservation) { return bulkResponse(reservation); }
            public UserVoucher lockWallet(Long voucherId) {
                return walletVoucherForUpdate(request.getUserPrincipalId(), request.getUserId(), voucherId);
            }
            public String walletStatus(UserVoucher wallet) { return wallet.getStatus() == null ? null : wallet.getStatus().name(); }
            public Voucher lockVoucher(Long voucherId) {
                return voucherRepository.findByIdForUpdate(voucherId)
                        .orElseThrow(() -> new IllegalArgumentException("Voucher not found: " + voucherId));
            }
            public void requireCapacity(UserVoucher wallet, Voucher voucher) { ensureWalletCapacity(wallet, voucher); }
            public VoucherStackingCalculator.Calculation quote(Map<Long, Voucher> vouchers, PromotionCommands.Reserve command) {
                VoucherStackingCalculator.Calculation calculation = stackingCalculator.calculate(
                        vouchers.values(), request.getRestaurantId(), request.getSubtotal(),
                        request.getGrossShippingFee(), command.voucherIds(), VoucherSelectionMode.MANUAL, LocalDateTime.now());
                Set<Long> appliedIds = calculation.appliedVouchers().stream()
                        .map(VoucherStackingCalculator.AppliedVoucher::voucherId).collect(Collectors.toSet());
                if (!appliedIds.equals(new HashSet<>(command.voucherIds()))) {
                    throw new PromotionConflictException("Voucher selection is no longer eligible");
                }

                return calculation;
            }
            public RuntimeException conflict(String message) { return new PromotionConflictException(message); }
            public PromotionReservationResponse persist(Map<Long, UserVoucher> wallets, Map<Long, Voucher> vouchers,
                    VoucherStackingCalculator.Calculation calculation, PromotionCommands.Reserve command) {
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
                for (Long voucherId : command.voucherIds()) {
                    VoucherStackingCalculator.AppliedVoucher applied = appliedById.get(voucherId);
                    UserVoucher wallet = wallets.get(voucherId);
                    Voucher voucher = vouchers.get(voucherId);
                    ReservationPolicy.Counters counters = ReservationPolicy.reserve(voucher.getUsedQuantity(),
                            wallet.getReservedCount(), wallet.getUsedCount(), true);
                    wallet.setReservedCount(counters.reserved());
                    wallet.setStatus(UserVoucher.Status.RESERVED);
                    wallet.setOrderId(request.getOrderId());
                    voucher.setUsedQuantity(counters.global());
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
        });
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
        return transitions.transition(new PromotionCommands.Transition(true, true),
                bulkTransitionPort(reservationId, orderId, userPrincipalId, enforcePrincipal));
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
        return transitions.transition(new PromotionCommands.Transition(true, false),
                bulkTransitionPort(reservationId, orderId, userPrincipalId, enforcePrincipal));
    }

    private void requireBulkReservationPrincipal(PromotionReservation reservation, Long userPrincipalId) {
        validatePositiveId(userPrincipalId, "userPrincipalId");
        String failure = ReservationReplayPolicy.principalFailure(reservation.getUserPrincipalId(), userPrincipalId);
        if (failure != null) throw new PromotionConflictException(failure);
    }

    @Transactional
    public int expirePromotionReservations() {
        return transitions.expire(bulkTransitionPort(null, null, null, false));
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
            ReservationPolicy.Counters counters = ReservationPolicy.transition(voucher.getUsedQuantity(),
                    wallet.getReservedCount(), wallet.getUsedCount(), wasCommitted,
                    target == PromotionReservation.State.COMMITTED, true);
            if (counters.failure() != null) throw new IllegalStateException(counters.failure());
            if (target == PromotionReservation.State.COMMITTED) {
                wallet.setReservedCount(counters.reserved());
                wallet.setUsedCount(counters.used());
            } else {
                voucher.setUsedQuantity(counters.global());
                if (wasCommitted) wallet.setUsedCount(counters.used());
                else wallet.setReservedCount(counters.reserved());
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
        String failure = ReservationReplayPolicy.orderFailure(reservation.getOrderId(), orderId, true);
        if (failure != null) throw new PromotionConflictException(failure);
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
        String failure = ReservationReplayPolicy.failure(
                new ReservationReplayPolicy.Request(reservation.getReservationId(), reservation.getOrderId(),
                        reservation.getUserId(), reservation.getUserPrincipalId(), reservation.getRestaurantId(),
                        reservation.getSubtotal(), reservation.getGrossShippingFee(), existing),
                new ReservationReplayPolicy.Request(request.getReservationId(), request.getOrderId(),
                        request.getUserId(), request.getUserPrincipalId(), request.getRestaurantId(),
                        request.getSubtotal(), request.getGrossShippingFee(), voucherIds), true);
        if (failure != null) throw new PromotionConflictException(failure);
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
        String failure = WalletClaimPolicy.capacityFailure(
                wallet.getUsedCount(), wallet.getReservedCount(), voucher.getUsageLimitPerUser());
        if (failure != null) throw new PromotionConflictException(failure);
    }

    private void updateWalletCompatibilityState(UserVoucher wallet, Voucher voucher, Long orderId) {
        ReservationPolicy.WalletState state = ReservationPolicy.wallet(
                wallet.getUsedCount(), wallet.getReservedCount(), voucher.getUsageLimitPerUser());
        wallet.setStatus(UserVoucher.Status.valueOf(state.status()));
        wallet.setOrderId(state.bindOrder() ? orderId : null);
        wallet.setUsedAt(state.stampUsedAt() ? LocalDateTime.now() : null);
    }

    private void requireBulkRepositories() {
        if (promotionReservationRepository == null || promotionReservationLineRepository == null) {
            throw new IllegalStateException("Promotion stacking persistence is unavailable");
        }
    }

    @Transactional
    public VoucherReservationResponse reserveVoucher(ReserveRequest request) {
        return new ReserveVouchersUseCase().reserve(new ReservationPort<VoucherReservation, VoucherReservationResponse, UserVoucher, Voucher, BigDecimal>() {
            public PromotionCommands.Reserve prepare() {
                validateReserveRequest(request);
                return new PromotionCommands.Reserve(request.getReservationId(), request.getOrderId(), List.of(request.getVoucherId()));
            }
            public Optional<VoucherReservation> findById() { return voucherReservationRepository.findById(request.getReservationId()); }
            public Optional<VoucherReservation> findByOrder() { return voucherReservationRepository.findByOrderId(request.getOrderId()); }
            public void requireExactReplay(VoucherReservation reservation, PromotionCommands.Reserve command) { requireSameReservation(reservation, request); }
            public VoucherReservationResponse replayResult(VoucherReservation reservation) { return legacyReservationResponse(reservation); }
            public UserVoucher lockWallet(Long voucherId) {
                return walletVoucherForUpdate(request.getUserPrincipalId(), request.getUserId(), voucherId);
            }
            public String walletStatus(UserVoucher wallet) { return wallet.getStatus() == null ? null : wallet.getStatus().name(); }
            public Voucher lockVoucher(Long voucherId) {
                return voucherRepository.findByIdForUpdate(voucherId)
                        .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
            }
            public void requireCapacity(UserVoucher wallet, Voucher voucher) {  }
            public BigDecimal quote(Map<Long, Voucher> vouchers, PromotionCommands.Reserve command) {
                Voucher voucher = vouchers.get(request.getVoucherId());
                String unavailable = WalletVoucherPolicy.reservationUnavailableReason(voucher,
                        request.getRestaurantId(), request.getSubtotal(), LocalDateTime.now());
                if (unavailable != null) throw new IllegalArgumentException(unavailable);
                return calculateDiscount(voucher, request.getSubtotal(), request.getShippingFee());
            }
            public RuntimeException conflict(String message) { return new PromotionConflictException(message); }
            public VoucherReservationResponse persist(Map<Long, UserVoucher> wallets, Map<Long, Voucher> vouchers,
                    BigDecimal discount, PromotionCommands.Reserve command) {
                UserVoucher userVoucher = wallets.get(request.getVoucherId());
                Voucher voucher = vouchers.get(request.getVoucherId());
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
                voucher.setUsedQuantity(ReservationPolicy.reserve(voucher.getUsedQuantity(), null, null, false).global());
                try {
                    voucherReservationRepository.saveAndFlush(reservation);
                } catch (DataIntegrityViolationException ex) {
                    throw new PromotionConflictException("Conflicting voucher reservation", ex);
                }
                outboxService.enqueue(reservation);
                return legacyReservationResponse(reservation, voucher);
            }
        });
    }

    @Transactional
    public VoucherReservationResponse commitReservation(UUID reservationId, Long orderId) {
        return transitions.transition(new PromotionCommands.Transition(false, true),
                legacyTransitionPort(reservationId, orderId));
    }

    @Transactional
    public VoucherReservationResponse releaseReservation(UUID reservationId, Long orderId) {
        return transitions.transition(new PromotionCommands.Transition(false, false),
                legacyTransitionPort(reservationId, orderId));
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
        return transitions.expire(legacyTransitionPort(null, null));
    }

    private VoucherReservation lockedReservation(UUID reservationId, Long orderId) {
        if (reservationId == null) throw new IllegalArgumentException("reservationId is required");
        validatePositiveId(orderId, "orderId");
        VoucherReservation reservation = voucherReservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher reservation not found"));
        String failure = ReservationReplayPolicy.orderFailure(reservation.getOrderId(), orderId, false);
        if (failure != null) throw new PromotionConflictException(failure);
        return reservation;
    }

    private void releaseCapacity(VoucherReservation reservation, VoucherReservation.State state) {
        UserVoucher userVoucher = walletVoucherForUpdate(reservation.getUserPrincipalId(), reservation.getUserId(),
                reservation.getVoucherId());
        Voucher voucher = voucherRepository.findByIdForUpdate(reservation.getVoucherId())
                .orElseThrow(() -> new IllegalStateException("Reserved voucher is missing"));
        ReservationPolicy.Counters counters = ReservationPolicy.transition(voucher.getUsedQuantity(), null, null,
                false, false, false);
        if (counters.failure() != null) throw new IllegalStateException(counters.failure());
        voucher.setUsedQuantity(counters.global());
        userVoucher.setStatus(UserVoucher.Status.SAVED);
        userVoucher.setOrderId(null);
        userVoucher.setUsedAt(null);
        reservation.setState(state);
        outboxService.enqueue(reservation);
    }

    private BigDecimal calculateDiscount(Voucher voucher, BigDecimal subtotal, BigDecimal shippingFee) {
        return com.delivery.promotion.domain.LegacyDiscountCalculator.calculate(
                VoucherDomainMapper.snapshot(voucher), subtotal, shippingFee);
    }

    private void requireSameReservation(VoucherReservation reservation, ReserveRequest request) {
        String failure = ReservationReplayPolicy.failure(
                new ReservationReplayPolicy.Request(reservation.getReservationId(), reservation.getOrderId(),
                        reservation.getUserId(), reservation.getUserPrincipalId(), reservation.getRestaurantId(),
                        reservation.getSubtotal(), reservation.getShippingFee(), java.util.Collections.singletonList(reservation.getVoucherId())),
                new ReservationReplayPolicy.Request(request.getReservationId(), request.getOrderId(),
                        request.getUserId(), request.getUserPrincipalId(), request.getRestaurantId(),
                        request.getSubtotal(), request.getShippingFee(), java.util.Collections.singletonList(request.getVoucherId())), false);
        if (failure != null) throw new PromotionConflictException(failure);
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
                .filter(WalletVoucherPolicy::isCheckoutEligible)
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
                .filter(WalletVoucherPolicy::isCheckoutEligible)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Voucher> listAllVouchers() {
        return voucherRepository.findByDeletedAtIsNull(PageRequest.of(0, COMPATIBILITY_LIST_LIMIT)).getContent();
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
        VoucherLifecyclePolicy.approve(voucher, adminPrincipalId, LocalDateTime.now());
        return voucherRepository.save(voucher);
    }

    @Transactional
    public Voucher rejectShopVoucher(Long voucherId, Long adminPrincipalId, String reason) {
        validatePositiveId(voucherId, "voucherId");
        validatePositiveId(adminPrincipalId, "adminPrincipalId");
        Voucher voucher = voucherRepository.findByIdForUpdate(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
        VoucherLifecyclePolicy.reject(voucher, adminPrincipalId, reason, LocalDateTime.now());
        return voucherRepository.save(voucher);
    }

    @Transactional
    public Voucher setVoucherActive(Long voucherId, boolean active) {
        validatePositiveId(voucherId, "voucherId");
        Voucher voucher = voucherRepository.findByIdForUpdate(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
        VoucherLifecyclePolicy.setActive(voucher, active);
        return voucherRepository.save(voucher);
    }

    @Transactional
    public void deleteVoucher(Long id) {
        deleteVoucher(id, null, "deleted_by_request");
    }

    @Transactional
    public void deleteVoucher(Long id, Long actorPrincipalId, String reason) {
        validatePositiveId(id, "voucherId");
        Voucher voucher = voucherRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
        if (VoucherLifecyclePolicy.retire(voucher, actorPrincipalId, reason, LocalDateTime.now())) {
            voucherRepository.save(voucher);
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

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Voucher code is required");
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private ReservationTransitionPort<PromotionReservation, PromotionReservationResponse> bulkTransitionPort(
            UUID reservationId, Long orderId, Long principalId, boolean enforcePrincipal) {
        return new ReservationTransitionPort<>() {
            private List<PromotionReservationLine> lines;

            public PromotionReservation lock() {
                requireBulkRepositories();
                PromotionReservation reservation = lockedBulkReservation(reservationId, orderId);
                if (enforcePrincipal) requireBulkReservationPrincipal(reservation, principalId);
                lines = promotionReservationLineRepository.findByReservationIdForUpdateOrderByVoucherIdAsc(reservationId);
                return reservation;
            }
            public PromotionCommands.ReservationState state(PromotionReservation reservation) {
                return new PromotionCommands.ReservationState(reservation.getState() == null ? null : reservation.getState().name(), reservation.getExpiresAt());
            }
            public LocalDateTime now() { return LocalDateTime.now(); }
            public void transition(PromotionReservation reservation, String target) {
                if (lines == null) {
                    lines = promotionReservationLineRepository
                            .findByReservationIdForUpdateOrderByVoucherIdAsc(reservation.getReservationId());
                }
                transitionBulkReservation(reservation, lines, PromotionReservation.State.valueOf(target));
            }
            public PromotionReservationResponse result(PromotionReservation reservation) {
                return PromotionReservationResponse.from(reservation, lines);
            }
            public RuntimeException conflict(String message) { return new PromotionConflictException(message); }
            public List<PromotionReservation> expiryCandidates() {
                requireBulkRepositories();
                return promotionReservationRepository.findTop100ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        PromotionReservation.State.RESERVED, LocalDateTime.now());
            }
            public PromotionReservation lockCandidate(PromotionReservation candidate) {
                lines = null;
                return promotionReservationRepository.findByIdForUpdate(candidate.getReservationId()).orElse(null);
            }
        };
    }

    private ReservationTransitionPort<VoucherReservation, VoucherReservationResponse> legacyTransitionPort(
            UUID reservationId, Long orderId) {
        return new ReservationTransitionPort<>() {

            public VoucherReservation lock() { return lockedReservation(reservationId, orderId); }
            public PromotionCommands.ReservationState state(VoucherReservation reservation) {
                return new PromotionCommands.ReservationState(reservation.getState() == null ? null : reservation.getState().name(), reservation.getExpiresAt());
            }
            public LocalDateTime now() { return LocalDateTime.now(); }
            public void transition(VoucherReservation reservation, String target) {
                if ("COMMITTED".equals(target)) {
                    UserVoucher wallet = walletVoucherForUpdate(reservation.getUserPrincipalId(), reservation.getUserId(), reservation.getVoucherId());
                    wallet.setStatus(UserVoucher.Status.USED);
                    wallet.setUsedAt(LocalDateTime.now());
                    reservation.setState(VoucherReservation.State.COMMITTED);
                    outboxService.enqueue(reservation);
                } else {
                    releaseCapacity(reservation, VoucherReservation.State.valueOf(target));
                }
            }
            public VoucherReservationResponse result(VoucherReservation reservation) {
                return legacyReservationResponse(reservation);
            }
            public RuntimeException conflict(String message) { return new PromotionConflictException(message); }
            public List<VoucherReservation> expiryCandidates() {
                return voucherReservationRepository.findTop100ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        VoucherReservation.State.RESERVED, LocalDateTime.now());
            }
            public VoucherReservation lockCandidate(VoucherReservation candidate) {
                return voucherReservationRepository.findByIdForUpdate(candidate.getReservationId()).orElse(null);
            }
        };
    }
}
