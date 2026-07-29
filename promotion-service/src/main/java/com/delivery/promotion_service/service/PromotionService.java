package com.delivery.promotion_service.service;

import com.delivery.promotion_service.dto.CalculateResponse;
import com.delivery.promotion_service.dto.CartContextRequest;
import com.delivery.promotion_service.dto.ReserveRequest;
import com.delivery.promotion_service.entity.UserVoucher;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.entity.VoucherGroup;
import com.delivery.promotion_service.repository.UserVoucherRepository;
import com.delivery.promotion_service.repository.VoucherGroupRepository;
import com.delivery.promotion_service.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import com.delivery.promotion_service.exception.PromotionConflictException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

        return CalculateResponse.builder()
                .availableVouchers(available)
                .unavailableVouchers(unavailable)
                .finalSubTotal(request.getSubTotal())
                .finalShippingFee(request.getShippingFee())
                .totalDiscount(BigDecimal.ZERO)
                .totalAmount(request.getSubTotal().add(request.getShippingFee()))
                .build();
    }

    private String checkVoucherAvailability(Voucher voucher, CartContextRequest request) {
        if (!Boolean.TRUE.equals(voucher.getActive())) return "Voucher is inactive";
        if (voucher.getEndTime() != null && voucher.getEndTime().isBefore(LocalDateTime.now())) return "Voucher expired";
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
    public void reserveVouchers(ReserveRequest request) {
        validateReserveRequest(request);
        List<Voucher> vouchersToApply = new ArrayList<>();
        Set<Long> appliedGroupIds = new HashSet<>();

        // Validate all vouchers first
        for (Long voucherId : request.getVoucherIds()) {
            UserVoucher uv = userVoucherRepository.findByUserIdAndVoucherId(request.getUserId(), voucherId)
                    .orElseThrow(() -> new IllegalArgumentException("User has not collected voucher " + voucherId));
            
            if (uv.getStatus() != UserVoucher.Status.SAVED) {
                throw new IllegalArgumentException("Voucher " + voucherId + " is not in SAVED state");
            }

            Voucher voucher = voucherRepository.findById(voucherId)
                    .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));

            // Check availability again
            if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
                throw new IllegalArgumentException("Voucher " + voucher.getCode() + " is out of stock");
            }

            // Stacking validation
            if (voucher.getVoucherGroupId() != null) {
                VoucherGroup group = voucherGroupRepository.findById(voucher.getVoucherGroupId()).orElse(null);
                if (group != null) {
                    if (appliedGroupIds.contains(group.getId())) {
                         throw new IllegalArgumentException("Cannot apply multiple vouchers from group: " + group.getName());
                    }
                    // Check mutual exclusions
                    for (Long exclId : group.getExcludedGroupIds()) {
                        if (appliedGroupIds.contains(exclId)) {
                             throw new IllegalArgumentException("Cannot combine voucher " + voucher.getCode() + " with other selected vouchers");
                        }
                    }
                    appliedGroupIds.add(group.getId());
                }
            }

            vouchersToApply.add(voucher);
            uv.setStatus(UserVoucher.Status.RESERVED);
            uv.setOrderId(request.getOrderId());
            uv.setUsedAt(LocalDateTime.now());
            userVoucherRepository.save(uv);
        }

        // Lock quantities
        for (Voucher voucher : vouchersToApply) {
            voucher.setUsedQuantity(voucher.getUsedQuantity() + 1);
            voucherRepository.save(voucher);
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
        if (request.getCreatorType() == Voucher.CreatorType.MERCHANT
                && (request.getCreatorId() == null || request.getCreatorId() <= 0)) {
            throw new IllegalArgumentException("Voucher creatorId must be positive for merchant vouchers");
        }
        if (request.getScopeType() != Voucher.ScopeType.ALL
                && (request.getScopeRefId() == null || request.getScopeRefId() <= 0)) {
            throw new IllegalArgumentException("Voucher scopeRefId must be positive for scoped vouchers");
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
        if (request.getVoucherIds() == null || request.getVoucherIds().isEmpty()) {
            throw new IllegalArgumentException("At least one voucherId is required");
        }
        Set<Long> uniqueIds = new HashSet<>();
        for (Long voucherId : request.getVoucherIds()) {
            validatePositiveId(voucherId, "voucherId");
            if (!uniqueIds.add(voucherId)) {
                throw new IllegalArgumentException("Duplicate voucherIds are not allowed");
            }
        }
    }

    private void validatePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
