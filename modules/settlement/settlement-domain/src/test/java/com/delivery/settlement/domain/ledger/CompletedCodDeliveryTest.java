package com.delivery.settlement.domain.ledger;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CompletedCodDeliveryTest {
    @Test
    void canonicalCodPostsNetEarningsCashAndCommissionInOriginalOrder() {
        var delivery = delivery(null, null, null, null, null, null, new BigDecimal("120000"));
        var plan = delivery.plan();
        assertEquals(3, plan.beforeHoldConsumption().size());
        assertEquals(LedgerPosting.Reason.ORDER_EARNING, plan.beforeHoldConsumption().get(0).reason());
        assertEquals(LedgerPosting.Reason.DELIVERY_FEE, plan.beforeHoldConsumption().get(1).reason());
        var cash = plan.beforeHoldConsumption().get(2);
        assertEquals(LedgerPosting.Wallet.DEPOSIT, cash.wallet());
        assertEquals(LedgerPosting.Direction.DEBIT, cash.direction());
        assertEquals(new BigDecimal("120000"), cash.amount());
        assertEquals(new BigDecimal("23000"), plan.platformCommission().amount());
    }

    @Test
    void promotionSubsidyIsDebitedBeforeDeliveryFeeAndCashUsesDiscountedTotal() {
        var plan = delivery(new BigDecimal("20000"), new BigDecimal("15000"),
                new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("5000"),
                new BigDecimal("5000"), new BigDecimal("115000")).plan();
        assertEquals(4, plan.beforeHoldConsumption().size());
        var subsidy = plan.beforeHoldConsumption().get(1);
        assertEquals(LedgerPosting.Reason.PROMOTION_SUBSIDY, subsidy.reason());
        assertEquals(LedgerPosting.Direction.DEBIT, subsidy.direction());
        assertEquals(new BigDecimal("5000"), subsidy.amount());
        assertEquals(new BigDecimal("115000"), plan.beforeHoldConsumption().get(3).amount());
    }

    @Test
    void invalidPromotionAndUnreconciledCashCannotProduceAPostingPlan() {
        assertThrows(IllegalArgumentException.class, () -> delivery(new BigDecimal("20000"),
                new BigDecimal("15000"), new BigDecimal("100000"), BigDecimal.ZERO,
                new BigDecimal("4000"), new BigDecimal("5000"), new BigDecimal("116000")).plan());
        assertThrows(IllegalArgumentException.class, () -> delivery(null, null, null,
                null, null, null, new BigDecimal("119999")).plan());
    }

    @Test
    void invalidIdentityAndMoneySnapshotsCannotProduceAnyPlan() {
        java.util.List<java.util.function.Consumer<Fixture>> invalid = java.util.List.of(
                f -> f.eventId = null, f -> f.eventType = "OTHER", f -> f.deliveryId = null,
                f -> f.deliveryId = 0L, f -> f.orderId = 0L, f -> f.restaurantId = 0L,
                f -> f.shipperId = 0L, f -> f.paymentMethod = "ONLINE",
                f -> f.restaurantEarnings = null, f -> f.restaurantEarnings = BigDecimal.ZERO,
                f -> f.shipperEarnings = null, f -> f.shipperEarnings = BigDecimal.ZERO,
                f -> f.shippingFee = null, f -> f.shippingFee = BigDecimal.ZERO,
                f -> f.restaurantCommission = null, f -> f.restaurantCommission = new BigDecimal("-1"),
                f -> f.shippingCommission = null, f -> f.shippingCommission = new BigDecimal("-1"),
                f -> f.platform = null, f -> f.platform = BigDecimal.ZERO,
                f -> f.platform = new BigDecimal("23001"), f -> f.shippingFee = new BigDecimal("20001"),
                f -> f.total = null, f -> f.total = BigDecimal.ZERO);
        for (var corrupt : invalid) {
            var fixture = new Fixture();
            corrupt.accept(fixture);
            assertThrows(IllegalArgumentException.class, () -> fixture.snapshot().plan());
        }
    }

    @Test
    void promotionAttributionRequiresBothDiscountsAndSufficientSubsidy() {
        java.util.List<java.util.function.Consumer<Fixture>> invalid = java.util.List.of(
                f -> f.shopDiscount = null, f -> f.shopDiscount = new BigDecimal("-1"),
                f -> f.shippingDiscount = null, f -> f.shippingDiscount = new BigDecimal("-1"),
                f -> f.shippingDiscount = BigDecimal.ONE);
        for (var corrupt : invalid) {
            var fixture = new Fixture();
            fixture.subtotal = new BigDecimal("100000");
            fixture.customerFee = new BigDecimal("20000");
            corrupt.accept(fixture);
            assertThrows(IllegalArgumentException.class, () -> fixture.snapshot().plan());
        }
    }

    @Test
    void missingOneAttributionFieldRetainsLegacyReconciliationAndDefaultGrossAndSubsidy() {
        var fixture = new Fixture();
        fixture.customerFee = new BigDecimal("20000");
        assertEquals(3, fixture.snapshot().plan().beforeHoldConsumption().size());
        fixture.subtotal = new BigDecimal("100000");
        fixture.customerFee = null;
        assertEquals(3, fixture.snapshot().plan().beforeHoldConsumption().size());
        fixture.customerFee = new BigDecimal("20000");
        assertEquals(3, fixture.snapshot().plan().beforeHoldConsumption().size());
    }

    private static final class Fixture {
        UUID eventId = UUID.randomUUID();
        String eventType = "DELIVERY_COMPLETED", paymentMethod = "COD";
        Long deliveryId = 1L, orderId = 101L, restaurantId = 11L, shipperId = 22L;
        BigDecimal restaurantEarnings = new BigDecimal("80000"), shipperEarnings = new BigDecimal("17000");
        BigDecimal restaurantCommission = new BigDecimal("20000"), shippingCommission = new BigDecimal("3000");
        BigDecimal platform = new BigDecimal("23000"), shippingFee = new BigDecimal("20000");
        BigDecimal gross, customerFee, subtotal, subsidy;
        BigDecimal shopDiscount = BigDecimal.ZERO, shippingDiscount = BigDecimal.ZERO, total = new BigDecimal("120000");
        CompletedCodDelivery snapshot() {
            return new CompletedCodDelivery(eventId, eventType, deliveryId, orderId, restaurantId, shipperId,
                    paymentMethod, restaurantEarnings, shipperEarnings, restaurantCommission, shippingCommission,
                    platform, shippingFee, gross, customerFee, subtotal, shopDiscount, subsidy, shippingDiscount, total);
        }
    }

    private CompletedCodDelivery delivery(BigDecimal gross, BigDecimal customerFee, BigDecimal subtotal,
            BigDecimal shopDiscount, BigDecimal subsidy, BigDecimal shippingDiscount, BigDecimal total) {
        return new CompletedCodDelivery(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "DELIVERY_COMPLETED", 1L, 101L, 11L, 22L, "COD", new BigDecimal("80000"),
                new BigDecimal("17000"), new BigDecimal("20000"), new BigDecimal("3000"),
                new BigDecimal("23000"), new BigDecimal("20000"), gross, customerFee, subtotal,
                shopDiscount, subsidy, shippingDiscount, total);
    }
}
