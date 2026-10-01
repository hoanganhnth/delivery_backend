package com.delivery.analytics_service.service;

import com.delivery.analytics_service.entity.AnalyticsEvent;
import com.delivery.analytics_service.entity.DailyRevenueStats;
import com.delivery.analytics_service.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalyticsPaymentProjectionTest {
    @ParameterizedTest
    @CsvSource({"false,12.5", "true,12.5", "false,NULL", "true,NULL"})
    void completedPaymentAccumulatesOnceAndNormalizesAbsentAmount(boolean existing, String value) {
        var fixture = new Fixture(false);
        Double amount = value.equals("NULL") ? null : Double.valueOf(value);
        var initial = row();
        if (existing) when(fixture.revenue.findByStatDateAndRestaurantIdIsNull(any()))
                .thenReturn(Optional.of(initial));

        fixture.service.processPaymentCompleted(1L, 2L, amount, "COD", "{\"eventId\":\"completed\"}");
        fixture.service.processPaymentCompleted(1L, 2L, amount, "COD", "{\"eventId\":\"completed\"}");

        var saved = ArgumentCaptor.forClass(DailyRevenueStats.class);
        verify(fixture.revenue).save(saved.capture());
        assertThat(saved.getValue().getSuccessfulPayments()).isEqualTo(existing ? 4 : 1);
        assertThat(saved.getValue().getFailedPayments()).isEqualTo(existing ? 2 : 0);
        BigDecimal increment = amount == null ? BigDecimal.ZERO : BigDecimal.valueOf(amount);
        assertThat(saved.getValue().getTotalPaymentAmount())
                .isEqualByComparingTo((existing ? new BigDecimal("20") : BigDecimal.ZERO).add(increment));
        verifyNoInteractions(fixture.orders, fixture.items);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedPaymentIncrementsFailureOnlyAndExactReplayIsNoOp(boolean existing) {
        var fixture = new Fixture(false);
        if (existing) when(fixture.revenue.findByStatDateAndRestaurantIdIsNull(any()))
                .thenReturn(Optional.of(row()));
        fixture.service.processPaymentFailed(1L, "{\"eventId\":\"failed\"}");
        fixture.service.processPaymentFailed(1L, "{\"eventId\":\"failed\"}");
        var saved = ArgumentCaptor.forClass(DailyRevenueStats.class);
        verify(fixture.revenue).save(saved.capture());
        assertThat(saved.getValue().getFailedPayments()).isEqualTo(existing ? 3 : 1);
        assertThat(saved.getValue().getSuccessfulPayments()).isEqualTo(existing ? 3 : 0);
        assertThat(saved.getValue().getTotalPaymentAmount()).isEqualByComparingTo(existing ? "20" : "0");
        verifyNoInteractions(fixture.orders, fixture.items);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void postgresUsesAtomicIncrementInsteadOfReadModifyWrite(boolean completed) {
        var fixture = new Fixture(true);
        if (completed) {
            fixture.service.processPaymentCompleted(1L, 2L, null, "COD", "{}");
            verify(fixture.revenue).incrementPaymentCompletedPostgres(any(LocalDate.class), eq(BigDecimal.ZERO));
        } else {
            fixture.service.processPaymentFailed(1L, "{}");
            verify(fixture.revenue).incrementPaymentFailedPostgres(any(LocalDate.class));
        }
        verifyNoMoreInteractions(fixture.revenue);
        verifyNoInteractions(fixture.orders, fixture.items);
    }

    private static DailyRevenueStats row() {
        return DailyRevenueStats.builder().statDate(LocalDate.now()).successfulPayments(3)
                .failedPayments(2).totalPaymentAmount(new BigDecimal("20"))
                .totalWithdrawals(BigDecimal.ZERO).platformFee(BigDecimal.ZERO).build();
    }

    private static class Fixture {
        final AnalyticsEventRepository events = mock(AnalyticsEventRepository.class);
        final DailyRevenueStatsRepository revenue = mock(DailyRevenueStatsRepository.class);
        final DailyOrderStatsRepository orders = mock(DailyOrderStatsRepository.class);
        final DailyItemSalesRepository items = mock(DailyItemSalesRepository.class);
        final EventProcessingService service = new EventProcessingService(events, orders, revenue, items);

        Fixture(boolean postgres) {
            Map<String, AnalyticsEvent> receipts = new HashMap<>();
            when(events.findByDeduplicationKey(anyString()))
                    .thenAnswer(call -> Optional.ofNullable(receipts.get(call.getArgument(0))));
            when(events.saveAndFlush(any())).thenAnswer(call -> {
                AnalyticsEvent receipt = call.getArgument(0);
                receipts.put(receipt.getDeduplicationKey(), receipt);
                return receipt;
            });
            if (postgres) {
                ReflectionTestUtils.setField(service, "dataSourceUrl", "jdbc:postgresql://localhost/analytics");
                when(events.insertIfAbsentPostgres(anyString(), anyString(), any(), any(), any(), any(),
                        any(), any(), any(), anyString(), anyString(), any())).thenReturn(1);
            }
        }
    }
}
