package com.delivery.settlement.domain.ledger;

import com.delivery.settlement.domain.EntityType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Direction.*;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Reason.*;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Wallet.EARNINGS;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Wallet.DEPOSIT;

/** Canonical completion snapshot. Preserves the existing COD reconciliation policy. */
public record CompletedCodDelivery(UUID eventId, String eventType, Long deliveryId, Long orderId,
        Long restaurantId, Long shipperId, String paymentMethod, BigDecimal restaurantEarnings,
        BigDecimal shipperEarnings, BigDecimal restaurantCommission, BigDecimal shippingCommission,
        BigDecimal totalPlatformEarnings, BigDecimal shippingFee, BigDecimal grossShippingFee,
        BigDecimal customerShippingFee, BigDecimal subtotalPrice, BigDecimal shopDiscount,
        BigDecimal platformSubsidy, BigDecimal shippingDiscount, BigDecimal totalPrice) {

    /** Identity is checked even for isolated simulation completions, before money validation. */
    public void requireCanonicalIdentity() {
        if (eventId == null || !"DELIVERY_COMPLETED".equals(eventType)
                || !positive(deliveryId) || !positive(orderId) || !positive(restaurantId) || !positive(shipperId)) {
            throw new IllegalArgumentException("canonical event type and identity fields are required");
        }
        if (!"COD".equals(paymentMethod)) {
            throw new IllegalArgumentException("MVP settlement only accepts COD");
        }
    }

    public CodSettlementPlan plan() {
        requireCanonicalIdentity();
        if (!positive(restaurantEarnings)) {
            throw new IllegalArgumentException("restaurantEarnings is null or <= 0");
        }
        if (!positive(shipperEarnings)) {
            throw new IllegalArgumentException("shipperEarnings is null or <= 0");
        }
        if (!positive(shippingFee) || !nonNegative(restaurantCommission)
                || !nonNegative(shippingCommission) || !positive(totalPlatformEarnings)) {
            throw new IllegalArgumentException("canonical fee/commission fields are missing or invalid");
        }
        BigDecimal platformEarnings = restaurantCommission.add(shippingCommission);
        if (totalPlatformEarnings.compareTo(platformEarnings) != 0) {
            throw new IllegalArgumentException("totalPlatformEarnings does not match commissions");
        }
        if (shippingFee.compareTo(shipperEarnings.add(shippingCommission)) != 0) {
            throw new IllegalArgumentException("shippingFee does not match shipper earnings and commission");
        }
        BigDecimal gross = grossShippingFee == null ? shippingFee : grossShippingFee;
        BigDecimal subsidy = platformSubsidy == null ? BigDecimal.ZERO : platformSubsidy;
        BigDecimal reconciledTotal;
        if (subtotalPrice != null && customerShippingFee != null) {
            if (!nonNegative(shopDiscount) || !nonNegative(shippingDiscount)
                    || subsidy.compareTo(shippingDiscount) < 0) {
                throw new IllegalArgumentException("promotion attribution fields are invalid");
            }
            reconciledTotal = restaurantEarnings.add(restaurantCommission).add(gross).subtract(subsidy);
        } else {
            reconciledTotal = restaurantEarnings.add(restaurantCommission).add(shippingFee);
        }
        if (!positive(totalPrice) || totalPrice.compareTo(reconciledTotal) != 0) {
            throw new IllegalArgumentException("totalPrice does not reconcile with restaurant and shipping amounts");
        }
        var postings = new ArrayList<LedgerPosting>();
        postings.add(new LedgerPosting(restaurantId, EntityType.RESTAURANT, orderId, CREDIT,
                ORDER_EARNING, restaurantEarnings, "Doanh thu đơn #" + orderId + " (đã trừ hoa hồng)", EARNINGS));
        if (subsidy.signum() > 0) {
            postings.add(new LedgerPosting(0L, EntityType.SYSTEM, orderId, DEBIT, PROMOTION_SUBSIDY,
                    subsidy, "Chi phí voucher nền tảng đơn #" + orderId, EARNINGS));
        }
        postings.add(new LedgerPosting(shipperId, EntityType.SHIPPER, orderId, CREDIT, DELIVERY_FEE,
                shipperEarnings, "Tiền công giao đơn #" + orderId, EARNINGS));
        postings.add(new LedgerPosting(shipperId, EntityType.SHIPPER, orderId, DEBIT, COD_SETTLEMENT,
                totalPrice, "Đối trừ COD đơn #" + orderId + " (shipper đã thu " + totalPrice + " tiền mặt)", DEPOSIT));
        return new CodSettlementPlan(postings, new LedgerPosting(0L, EntityType.SYSTEM, orderId, CREDIT,
                PLATFORM_COMMISSION, platformEarnings, "Hoa hồng nền tảng đơn #" + orderId, EARNINGS));
    }

    private static boolean positive(Long value) { return value != null && value > 0; }
    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private static boolean nonNegative(BigDecimal value) { return value != null && value.signum() >= 0; }
}
