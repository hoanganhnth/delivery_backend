package com.delivery.promotion.application;

import com.delivery.promotion.application.api.*;
import com.delivery.promotion.domain.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CollectionAndPricingUseCasesTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);
    private static VoucherSnapshot voucher() {
        return new VoucherSnapshot(1L, "CODE", Voucher.CreatorType.PLATFORM, Voucher.RewardType.FIXED,
                Voucher.ScopeType.ALL, null, BigDecimal.TEN, null, BigDecimal.ZERO, 10, 0, true,
                NOW.minusDays(1), NOW.plusDays(1), null, "PLATFORM_DISCOUNT", "APPROVED");
    }
    @Test void collectionChecksEligibilityBeforeDuplicateAndSave() {
        var port = new CollectionFake(); new CollectVoucherUseCase().collect(port);
        assertThat(port.calls).containsExactly("validate", "find", "now", "snapshot", "existing", "save");
        port = new CollectionFake(); port.duplicate = true;
        var duplicate = port;
        assertThatThrownBy(() -> new CollectVoucherUseCase().collect(duplicate)).hasMessage("Voucher already collected");
        assertThat(port.calls).containsExactly("validate", "find", "now", "snapshot", "existing", "duplicate");
        port = new CollectionFake(); port.unavailable = true;
        var unavailable = port;
        assertThatThrownBy(() -> new CollectVoucherUseCase().collect(unavailable)).hasMessage("Voucher is not checkout-eligible");
        assertThat(port.calls).containsExactly("validate", "find", "now", "snapshot");
    }
    @Test void collectionPropagatesIdentityLookupAndConcurrentSaveFailures() {
        for (String stage : List.of("validate", "find", "save")) {
            var port = new CollectionFake(); port.failStage = stage;
            assertThatThrownBy(() -> new CollectVoucherUseCase().collect(port)).hasMessage(stage);
            assertThat(port.calls.get(port.calls.size() - 1)).isEqualTo(stage);
        }
    }
    @Test void pricingPreservesDomainQuoteAndPerformsReadsBeforeProjection() {
        var calls = new ArrayList<String>();
        var command = new PromotionCommands.Pricing(List.of(voucher()), 7L, new BigDecimal("100.00"),
                new BigDecimal("20.00"), List.of(1L), VoucherSelectionMode.MANUAL, NOW);
        var expected = new VoucherStackingCalculator().calculate(command.vouchers(), 7L, command.subtotal(),
                command.shipping(), command.selectedIds(), command.mode(), NOW);
        var result = new CalculateVouchersUseCase().calculate(new PricingPort<String, VoucherStackingCalculator.Calculation>() {
            public void validate() { calls.add("validate"); }
            public String loadWalletVouchers() { calls.add("load"); return "wallet"; }
            public PromotionCommands.Pricing pricing(String context) { assertThat(context).isEqualTo("wallet"); calls.add("pricing"); return command; }
            public VoucherStackingCalculator.Calculation result(String context, VoucherStackingCalculator.Calculation calculation) {
                assertThat(context).isEqualTo("wallet"); calls.add("result"); return calculation;
            }
        });
        assertThat(result).isEqualTo(expected); assertThat(result.totalAmount()).isEqualByComparingTo("110.00");
        assertThat(calls).containsExactly("validate", "load", "pricing", "result");
    }
    static class CollectionFake implements CollectionPort<VoucherSnapshot> {
        final List<String> calls = new ArrayList<>(); boolean duplicate, unavailable; String failStage;
        void call(String stage) { calls.add(stage); if (stage.equals(failStage)) throw new IllegalArgumentException(stage); }
        public void validateIdentity() { call("validate"); }
        public VoucherSnapshot findVoucher() { call("find"); return voucher(); }
        public Voucher snapshot(VoucherSnapshot voucher) { call("snapshot"); return unavailable ? null : voucher; }
        public LocalDateTime now() { call("now"); return NOW; }
        public boolean alreadyCollected(VoucherSnapshot voucher) { call("existing"); return duplicate; }
        public RuntimeException duplicate(VoucherSnapshot voucher, String message) { call("duplicate"); return new IllegalStateException(message); }
        public void save(VoucherSnapshot voucher) { call("save"); }
    }
}
