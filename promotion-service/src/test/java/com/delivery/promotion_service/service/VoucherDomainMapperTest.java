package com.delivery.promotion_service.service;

import com.delivery.promotion_service.dto.VoucherSelectionMode;
import com.delivery.promotion_service.entity.Voucher;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VoucherDomainMapperTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 30, 12, 0);

    @Test
    void snapshotPreservesAllPricingFieldsWithoutNormalizingOrMutatingEntity() {
        Voucher voucher = Voucher.builder().id(7L).code(" MIXED ").creatorType(Voucher.CreatorType.SHOP)
                .rewardType(Voucher.RewardType.PERCENTAGE).scopeType(Voucher.ScopeType.SHOP).scopeRefId(9L)
                .discountValue(new BigDecimal("10.005")).maxDiscountValue(new BigDecimal("20.050"))
                .minOrderValue(new BigDecimal("1.000")).totalQuantity(100).usedQuantity(2).active(false)
                .startTime(NOW.minusDays(1)).endTime(NOW.plusDays(1)).deletedAt(NOW)
                .layerCode(" shop_discount ").approvalStatus("approved").build();
        var snapshot = VoucherDomainMapper.snapshot(voucher);
        assertThat(snapshot.getId()).isEqualTo(voucher.getId());
        assertThat(snapshot.getCode()).isEqualTo(voucher.getCode());
        assertThat(snapshot.getCreatorType().name()).isEqualTo(voucher.getCreatorType().name());
        assertThat(snapshot.getRewardType().name()).isEqualTo(voucher.getRewardType().name());
        assertThat(snapshot.getScopeType().name()).isEqualTo(voucher.getScopeType().name());
        assertThat(snapshot.getScopeRefId()).isEqualTo(voucher.getScopeRefId());
        assertThat(snapshot.getDiscountValue()).isEqualTo(voucher.getDiscountValue());
        assertThat(snapshot.getMaxDiscountValue()).isEqualTo(voucher.getMaxDiscountValue());
        assertThat(snapshot.getMinOrderValue()).isEqualTo(voucher.getMinOrderValue());
        assertThat(snapshot.getTotalQuantity()).isEqualTo(voucher.getTotalQuantity());
        assertThat(snapshot.getUsedQuantity()).isEqualTo(voucher.getUsedQuantity());
        assertThat(snapshot.getActive()).isEqualTo(voucher.getActive());
        assertThat(snapshot.getStartTime()).isEqualTo(voucher.getStartTime());
        assertThat(snapshot.getEndTime()).isEqualTo(voucher.getEndTime());
        assertThat(snapshot.getDeletedAt()).isEqualTo(voucher.getDeletedAt());
        assertThat(snapshot.getLayerCode()).isEqualTo(voucher.getLayerCode());
        assertThat(snapshot.getApprovalStatus()).isEqualTo(voucher.getApprovalStatus());
        voucher.setCode("CHANGED");
        voucher.setDiscountValue(BigDecimal.ZERO);
        assertThat(snapshot.getCode()).isEqualTo(" MIXED ");
        assertThat(snapshot.getDiscountValue()).isEqualTo(new BigDecimal("10.005"));
    }

    @Test
    void nullAndEveryPersistedEnumValueMapWithoutNewValidation() {
        assertThat(VoucherDomainMapper.snapshot(null)).isNull();
        var empty = VoucherDomainMapper.snapshot(new Voucher());
        assertThat(empty.getCreatorType()).isNull();
        assertThat(empty.getRewardType()).isNull();
        assertThat(empty.getScopeType()).isNull();
        for (Voucher.CreatorType creator : Voucher.CreatorType.values()) {
            for (Voucher.RewardType reward : Voucher.RewardType.values()) {
                for (Voucher.ScopeType scope : Voucher.ScopeType.values()) {
                    var mapped = VoucherDomainMapper.snapshot(Voucher.builder()
                            .creatorType(creator).rewardType(reward).scopeType(scope).build());
                    assertThat(mapped.getCreatorType().name()).isEqualTo(creator.name());
                    assertThat(mapped.getRewardType().name()).isEqualTo(reward.name());
                    assertThat(mapped.getScopeType().name()).isEqualTo(scope.name());
                }
            }
        }
    }

    @Test
    void hostQuoteMapsEveryDomainResponseFieldAndPreservesNullCandidatesAndDefaultMode() {
        Voucher shop = voucher(1L, Voucher.CreatorType.SHOP, Voucher.RewardType.FIXED, "1.005");
        shop.setScopeType(Voucher.ScopeType.SHOP);
        shop.setScopeRefId(9L);
        Voucher platform = voucher(2L, Voucher.CreatorType.PLATFORM, Voucher.RewardType.PERCENTAGE, "33.35");
        platform.setMaxDiscountValue(new BigDecimal("2.335"));
        Voucher freeship = voucher(3L, Voucher.CreatorType.PLATFORM, Voucher.RewardType.FREESHIP, "1.005");
        Voucher inactive = voucher(4L, Voucher.CreatorType.PLATFORM, Voucher.RewardType.FIXED, "1");
        inactive.setActive(false);
        List<Voucher> vouchers = Arrays.asList(shop, platform, freeship, inactive, null);
        for (VoucherSelectionMode mode : Arrays.asList(null, VoucherSelectionMode.AUTO, VoucherSelectionMode.MANUAL)) {
            var host = new VoucherStackingCalculator().calculate(vouchers, 9L, new BigDecimal("10.005"),
                    new BigDecimal("3.005"), List.of(1L, 2L, 3L), mode, NOW);
            var domain = new com.delivery.promotion.domain.VoucherStackingCalculator().calculate(
                    vouchers.stream().map(VoucherDomainMapper::snapshot).toList(), 9L, new BigDecimal("10.005"),
                    new BigDecimal("3.005"), List.of(1L, 2L, 3L), mode == null ? null :
                            com.delivery.promotion.domain.VoucherSelectionMode.valueOf(mode.name()), NOW);
            assertThat(host).usingRecursiveComparison().isEqualTo(domain);
        }
        assertThat(new VoucherStackingCalculator().calculate(null, 9L, BigDecimal.TEN,
                BigDecimal.ONE, null, null, NOW).totalAmount()).isEqualTo(new BigDecimal("11.00"));
    }

    private Voucher voucher(Long id, Voucher.CreatorType creator, Voucher.RewardType reward, String value) {
        return Voucher.builder().id(id).code("CODE" + id).creatorType(creator).rewardType(reward)
                .scopeType(Voucher.ScopeType.ALL).discountValue(new BigDecimal(value))
                .totalQuantity(10).usedQuantity(0).startTime(NOW.minusDays(1)).endTime(NOW.plusDays(1))
                .active(true).build();
    }
}
