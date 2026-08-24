package com.delivery.promotion_service.service;

import com.delivery.promotion_service.dto.VoucherSelectionMode;
import com.delivery.promotion_service.entity.Voucher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure, deterministic pricing rule for the fixed three-layer voucher model.
 * It deliberately has no repository or clock dependency so the rule can be
 * proved independently from HTTP, PostgreSQL and Kafka behavior.
 */
public final class VoucherStackingCalculator {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Calculation calculate(
            Collection<Voucher> vouchers,
            Long restaurantId,
            BigDecimal subtotal,
            BigDecimal grossShippingFee,
            Collection<Long> selectedVoucherIds,
            VoucherSelectionMode selectionMode,
            LocalDateTime now) {
        requireNonNegative(subtotal, "subtotal");
        requireNonNegative(grossShippingFee, "shippingFee");
        if (restaurantId == null || restaurantId <= 0) {
            throw new IllegalArgumentException("restaurantId must be positive");
        }
        Objects.requireNonNull(now, "now");

        List<Voucher> input = vouchers == null ? List.of() : vouchers.stream()
                .filter(Objects::nonNull)
                .toList();
        List<UnavailableVoucher> unavailable = new ArrayList<>();
        EnumMap<VoucherLayer, List<Candidate>> eligible = new EnumMap<>(VoucherLayer.class);
        for (VoucherLayer layer : VoucherLayer.values()) eligible.put(layer, new ArrayList<>());

        Map<Long, Voucher> byId = new HashMap<>();
        for (Voucher voucher : input) {
            if (voucher.getId() == null || voucher.getId() <= 0) {
                unavailable.add(new UnavailableVoucher(voucher.getId(), voucher.getCode(), "Voucher ID is invalid"));
                continue;
            }
            byId.put(voucher.getId(), voucher);
            String reason = availabilityReason(voucher, restaurantId, subtotal, now);
            if (reason != null) {
                unavailable.add(new UnavailableVoucher(voucher.getId(), voucher.getCode(), reason));
                continue;
            }
            VoucherLayer layer;
            try {
                layer = VoucherLayerResolver.resolve(voucher);
            } catch (IllegalArgumentException invalidLayer) {
                unavailable.add(new UnavailableVoucher(voucher.getId(), voucher.getCode(),
                        invalidLayer.getMessage() == null ? "Voucher layer is invalid" : invalidLayer.getMessage()));
                continue;
            }
            eligible.get(layer).add(new Candidate(voucher, layer));
        }

        VoucherSelectionMode mode = selectionMode == null ? VoucherSelectionMode.AUTO : selectionMode;
        List<Long> selected = selectedVoucherIds == null ? List.of()
                : selectedVoucherIds.stream().filter(Objects::nonNull).toList();
        if (selected.size() > VoucherLayer.values().length) {
            throw new IllegalArgumentException("At most one voucher per layer is supported");
        }
        if (new HashSet<>(selected).size() != selected.size()) {
            throw new IllegalArgumentException("Duplicate voucher IDs are not allowed");
        }

        ScoredSelection best;
        if (mode == VoucherSelectionMode.MANUAL) {
            best = manualSelection(selected, byId, eligible, restaurantId, subtotal, grossShippingFee);
        } else {
            best = autoSelection(eligible, subtotal, grossShippingFee);
        }

        if (!isPayable(best, grossShippingFee)) {
            throw new IllegalArgumentException(
                    "Voucher combination leaves no positive payable food amount");
        }
        return toCalculation(best, unavailable);
    }

    private ScoredSelection manualSelection(
            List<Long> selected,
            Map<Long, Voucher> byId,
            Map<VoucherLayer, List<Candidate>> eligible,
            Long restaurantId,
            BigDecimal subtotal,
            BigDecimal grossShippingFee) {
        EnumMap<VoucherLayer, Candidate> byLayer = new EnumMap<>(VoucherLayer.class);
        for (Long id : selected) {
            Voucher voucher = byId.get(id);
            if (voucher == null) throw new IllegalArgumentException("Voucher is not in the wallet: " + id);
            VoucherLayer layer = VoucherLayerResolver.resolve(voucher);
            if (byLayer.put(layer, findCandidate(eligible.get(layer), id)) != null) {
                throw new IllegalArgumentException("Only one voucher per layer is allowed: " + layer);
            }
        }
        return score(byLayer.get(VoucherLayer.SHOP_DISCOUNT),
                byLayer.get(VoucherLayer.PLATFORM_DISCOUNT),
                byLayer.get(VoucherLayer.FREESHIP), subtotal, grossShippingFee);
    }

    private Candidate findCandidate(List<Candidate> candidates, Long id) {
        if (candidates != null) {
            for (Candidate candidate : candidates) {
                if (id.equals(candidate.voucher().getId())) return candidate;
            }
        }
        throw new IllegalArgumentException("Voucher is unavailable: " + id);
    }

    private ScoredSelection autoSelection(
            Map<VoucherLayer, List<Candidate>> eligible,
            BigDecimal subtotal,
            BigDecimal grossShippingFee) {
        ScoredSelection best = null;
        List<Candidate> shops = withEmpty(eligible.get(VoucherLayer.SHOP_DISCOUNT));
        List<Candidate> platforms = withEmpty(eligible.get(VoucherLayer.PLATFORM_DISCOUNT));
        List<Candidate> freeships = withEmpty(eligible.get(VoucherLayer.FREESHIP));
        for (Candidate shop : shops) {
            for (Candidate platform : platforms) {
                for (Candidate freeship : freeships) {
                    ScoredSelection candidate = score(shop, platform, freeship, subtotal, grossShippingFee);
                    if (!isPayable(candidate, grossShippingFee)) continue;
                    if (best == null || compare(candidate, best) > 0) best = candidate;
                }
            }
        }
        return best == null
                ? score(null, null, null, subtotal, grossShippingFee)
                : best;
    }

    private List<Candidate> withEmpty(List<Candidate> candidates) {
        List<Candidate> result = new ArrayList<>();
        result.add(null);
        if (candidates != null) result.addAll(candidates);
        return result;
    }

    private ScoredSelection score(
            Candidate shop,
            Candidate platform,
            Candidate freeship,
            BigDecimal subtotal,
            BigDecimal grossShippingFee) {
        BigDecimal shopDiscount = discount(shop == null ? null : shop.voucher(), subtotal, subtotal);
        BigDecimal platformBase = subtotal.subtract(shopDiscount).max(ZERO);
        BigDecimal platformDiscount = discount(platform == null ? null : platform.voucher(), platformBase, platformBase);
        BigDecimal freeshipDiscount = discount(freeship == null ? null : freeship.voucher(),
                grossShippingFee, grossShippingFee);
        BigDecimal customerShipping = grossShippingFee.subtract(freeshipDiscount).max(ZERO);
        BigDecimal itemDiscount = shopDiscount.add(platformDiscount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDiscount = itemDiscount.add(freeshipDiscount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(itemDiscount).add(customerShipping)
                .max(ZERO).setScale(2, RoundingMode.HALF_UP);
        return new ScoredSelection(shop, platform, freeship, subtotal, platformBase, grossShippingFee,
                shopDiscount, platformDiscount, freeshipDiscount, itemDiscount, totalDiscount,
                customerShipping, total);
    }

    private int compare(ScoredSelection left, ScoredSelection right) {
        int amount = left.totalDiscount().compareTo(right.totalDiscount());
        if (amount != 0) return amount;
        int expiry = expiryKey(left).compareTo(expiryKey(right));
        if (expiry != 0) return -expiry;
        return compareIds(left, right);
    }

    private String expiryKey(ScoredSelection selection) {
        return java.util.stream.Stream.of(selection.shop(), selection.platform(), selection.freeship())
                .filter(Objects::nonNull)
                .map(Candidate::voucher)
                .map(Voucher::getEndTime)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .map(LocalDateTime::toString)
                .orElse("9999-12-31T23:59:59");
    }

    private int compareIds(ScoredSelection left, ScoredSelection right) {
        List<Candidate> leftCandidates = java.util.Arrays.asList(left.shop(), left.platform(), left.freeship());
        List<Candidate> rightCandidates = java.util.Arrays.asList(right.shop(), right.platform(), right.freeship());
        for (int index = 0; index < leftCandidates.size(); index++) {
            long leftId = idOrMax(leftCandidates.get(index));
            long rightId = idOrMax(rightCandidates.get(index));
            // Lower stable ID wins the tie, so invert the numeric comparison.
            int comparison = Long.compare(rightId, leftId);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private long idOrMax(Candidate candidate) {
        return candidate == null || candidate.voucher().getId() == null
                ? Long.MAX_VALUE : candidate.voucher().getId();
    }

    private boolean isPayable(ScoredSelection selection, BigDecimal grossShippingFee) {
        return selection != null && selection.totalAmount().compareTo(grossShippingFee) > 0;
    }

    private Calculation toCalculation(ScoredSelection selection, List<UnavailableVoucher> unavailable) {
        List<AppliedVoucher> applied = new ArrayList<>();
        addApplied(applied, selection.shop(), VoucherLayer.SHOP_DISCOUNT,
                selection.shopDiscount(), selection.shopBase());
        addApplied(applied, selection.platform(), VoucherLayer.PLATFORM_DISCOUNT,
                selection.platformDiscount(), selection.platformBase());
        addApplied(applied, selection.freeship(), VoucherLayer.FREESHIP,
                selection.freeshipDiscount(), selection.freeshipBase());
        return new Calculation(applied, unavailable, selection.itemDiscount(), selection.freeshipDiscount(),
                selection.totalDiscount(), selection.customerShippingFee(), selection.totalAmount());
    }

    private void addApplied(List<AppliedVoucher> applied, Candidate candidate, VoucherLayer layer,
                            BigDecimal amount, BigDecimal base) {
        if (candidate == null) return;
        applied.add(new AppliedVoucher(candidate.voucher().getId(), candidate.voucher().getCode(), layer,
                amount, base == null ? ZERO : base.setScale(2, RoundingMode.HALF_UP),
                layer == VoucherLayer.SHOP_DISCOUNT ? "SHOP" : "PLATFORM"));
    }

    private String availabilityReason(Voucher voucher, Long restaurantId, BigDecimal subtotal, LocalDateTime now) {
        if (voucher.getCreatorType() == null) return "Voucher ownership is invalid";
        if (voucher.getRewardType() == null) return "Voucher reward type is invalid";
        if (voucher.getScopeType() == null) return "Voucher scope is invalid";
        if ((voucher.getScopeType() == Voucher.ScopeType.ALL && voucher.getScopeRefId() != null)
                || (voucher.getScopeType() == Voucher.ScopeType.SHOP && voucher.getScopeRefId() == null)) {
            return "Voucher scope identity is invalid";
        }
        if (!Boolean.TRUE.equals(voucher.getActive())) return "Voucher is inactive";
        if (voucher.getApprovalStatus() != null
                && !"APPROVED".equalsIgnoreCase(voucher.getApprovalStatus())) return "Voucher is not approved";
        if (voucher.getStartTime() != null && now.isBefore(voucher.getStartTime())) return "Voucher is not active yet";
        if (voucher.getEndTime() == null) return "Voucher expiration is invalid";
        if (!now.isBefore(voucher.getEndTime())) return "Voucher expired";
        if (voucher.getTotalQuantity() == null || voucher.getTotalQuantity() < 1
                || voucher.getUsedQuantity() == null || voucher.getUsedQuantity() < 0) {
            return "Voucher capacity is invalid";
        }
        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) return "Out of stock";
        BigDecimal minimum = voucher.getMinOrderValue() == null ? ZERO : voucher.getMinOrderValue();
        if (subtotal.compareTo(minimum) < 0) return "Need " + minimum.subtract(subtotal) + " more to use";
        VoucherLayer layer;
        try {
            layer = VoucherLayerResolver.resolve(voucher);
        } catch (IllegalArgumentException invalidLayer) {
            return invalidLayer.getMessage() == null ? "Voucher layer is invalid" : invalidLayer.getMessage();
        }
        if (voucher.getScopeType() == Voucher.ScopeType.CATEGORY) {
            return "Legacy CATEGORY voucher is not checkout-eligible";
        }
        if (voucher.getCreatorType() != Voucher.CreatorType.PLATFORM
                && voucher.getCreatorType() != Voucher.CreatorType.SHOP) {
            return "Voucher ownership is not checkout-eligible";
        }
        if (voucher.getCreatorType() == Voucher.CreatorType.SHOP
                && (layer != VoucherLayer.SHOP_DISCOUNT
                || voucher.getScopeType() != Voucher.ScopeType.SHOP)) {
            return "Shop voucher must use the SHOP_DISCOUNT layer and SHOP scope";
        }
        if (voucher.getCreatorType() == Voucher.CreatorType.PLATFORM
                && layer == VoucherLayer.SHOP_DISCOUNT) {
            return "Platform voucher cannot use the SHOP_DISCOUNT layer";
        }
        if (voucher.getScopeType() == Voucher.ScopeType.SHOP
                && (voucher.getScopeRefId() == null
                || !restaurantId.equals(voucher.getScopeRefId()))) return "Not applicable for this shop";
        if (layer == VoucherLayer.FREESHIP && voucher.getScopeType() != Voucher.ScopeType.ALL) {
            return "Freeship voucher must be platform-wide";
        }
        if (layer == VoucherLayer.FREESHIP && voucher.getRewardType() != Voucher.RewardType.FREESHIP) {
            return "Freeship layer requires a freeship reward";
        }
        if (layer != VoucherLayer.FREESHIP && voucher.getRewardType() == Voucher.RewardType.FREESHIP) {
            return "Freeship reward cannot be stacked as an item discount";
        }
        if (voucher.getDiscountValue() == null || voucher.getDiscountValue().signum() < 0) {
            return "Voucher discount is invalid";
        }
        if (voucher.getMinOrderValue() != null && voucher.getMinOrderValue().signum() < 0) {
            return "Voucher minimum order is invalid";
        }
        if (voucher.getMaxDiscountValue() != null && voucher.getMaxDiscountValue().signum() < 0) {
            return "Voucher max discount is invalid";
        }
        return null;
    }

    private BigDecimal discount(Voucher voucher, BigDecimal base, BigDecimal ignored) {
        if (voucher == null) return ZERO;
        BigDecimal value = switch (voucher.getRewardType()) {
            case FIXED -> voucher.getDiscountValue().min(base);
            case PERCENTAGE -> base.multiply(voucher.getDiscountValue())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP).min(base);
            case FREESHIP -> base.min(voucher.getDiscountValue());
        };
        if (voucher.getMaxDiscountValue() != null) value = value.min(voucher.getMaxDiscountValue());
        return value.max(ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(name + " must be non-negative");
    }

    private record Candidate(Voucher voucher, VoucherLayer layer) {
    }

    private record ScoredSelection(
            Candidate shop,
            Candidate platform,
            Candidate freeship,
            BigDecimal shopBase,
            BigDecimal platformBase,
            BigDecimal freeshipBase,
            BigDecimal shopDiscount,
            BigDecimal platformDiscount,
            BigDecimal freeshipDiscount,
            BigDecimal itemDiscount,
            BigDecimal totalDiscount,
            BigDecimal customerShippingFee,
            BigDecimal totalAmount) {
    }

    public record AppliedVoucher(
            Long voucherId,
            String code,
            VoucherLayer layer,
            BigDecimal discountAmount,
            BigDecimal discountBase,
            String fundingSource) {
    }

    public record UnavailableVoucher(Long voucherId, String code, String reason) {
    }

    public record Calculation(
            List<AppliedVoucher> appliedVouchers,
            List<UnavailableVoucher> unavailableVouchers,
            BigDecimal itemDiscount,
            BigDecimal shippingDiscount,
            BigDecimal totalDiscount,
            BigDecimal customerShippingFee,
            BigDecimal totalAmount) {
        public Calculation {
            appliedVouchers = List.copyOf(appliedVouchers);
            unavailableVouchers = List.copyOf(unavailableVouchers);
        }
    }
}
