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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.delivery.promotion_service.dto.CreateVoucherRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionService {

    private final VoucherRepository voucherRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final VoucherGroupRepository voucherGroupRepository;

    @Transactional
    public Voucher createVoucher(CreateVoucherRequest request) {
        if (voucherRepository.findByCode(request.getCode()).isPresent()) {
            throw new IllegalArgumentException("Voucher code already exists");
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
        return voucherRepository.save(voucher);
    }

    @Transactional
    public void collectVoucher(Long userId, String voucherCode) {
        Voucher voucher = voucherRepository.findByCode(voucherCode)
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));

        if (!voucher.getActive() || voucher.getEndTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Voucher is expired or inactive");
        }

        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            throw new IllegalArgumentException("Voucher is out of stock");
        }

        Optional<UserVoucher> existing = userVoucherRepository.findByUserIdAndVoucherId(userId, voucher.getId());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Voucher already collected");
        }

        UserVoucher userVoucher = UserVoucher.builder()
                .userId(userId)
                .voucherId(voucher.getId())
                .status(UserVoucher.Status.SAVED)
                .build();
        
        userVoucherRepository.save(userVoucher);
    }

    @Transactional(readOnly = true)
    public CalculateResponse calculate(CartContextRequest request) {
        List<UserVoucher> savedVouchers = userVoucherRepository.findByUserIdAndStatus(request.getUserId(), UserVoucher.Status.SAVED);
        
        List<CalculateResponse.VoucherInfo> available = new ArrayList<>();
        List<CalculateResponse.UnavailableVoucherInfo> unavailable = new ArrayList<>();

        for (UserVoucher uv : savedVouchers) {
            Voucher voucher = voucherRepository.findById(uv.getVoucherId()).orElse(null);
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
        if (!voucher.getActive()) return "Voucher is inactive";
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) return "Voucher expired";
        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) return "Out of stock";
        if (request.getSubTotal().compareTo(voucher.getMinOrderValue()) < 0) {
            return "Need " + voucher.getMinOrderValue().subtract(request.getSubTotal()) + " more to use";
        }
        if (voucher.getScopeType() == Voucher.ScopeType.SHOP && !voucher.getScopeRefId().equals(request.getShopId())) {
            return "Not applicable for this shop";
        }
        return null; // Available
    }

    @Transactional
    public void reserveVouchers(ReserveRequest request) {
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
}
