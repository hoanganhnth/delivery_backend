package com.delivery.promotion_service.dto;

import com.delivery.promotion_service.entity.Voucher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherResponseTest {

    @Test
    void mapsStableWireFieldsWithoutReturningPersistenceEntity() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 27, 2, 0);
        Voucher voucher = Voucher.builder()
                .id(11L)
                .code("WELCOME")
                .name("Welcome")
                .creatorType(Voucher.CreatorType.PLATFORM)
                .rewardType(Voucher.RewardType.FIXED)
                .discountValue(new BigDecimal("10000"))
                .scopeType(Voucher.ScopeType.ALL)
                .totalQuantity(100)
                .usedQuantity(3)
                .usageLimitPerUser(1)
                .startTime(now)
                .endTime(now.plusDays(7))
                .minOrderValue(new BigDecimal("50000"))
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        VoucherResponse response = VoucherResponse.from(voucher);

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.code()).isEqualTo("WELCOME");
        assertThat(response.discountValue()).isEqualByComparingTo("10000");
        assertThat(response.usedQuantity()).isEqualTo(3);
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(now);
    }
}
