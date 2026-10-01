package com.delivery.analytics_service.service;

import com.delivery.analytics_service.entity.DailyOrderStats;
import com.delivery.analytics_service.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalyticsDeliveredProjectionTest {
    @ParameterizedTest
    @CsvSource({"false,false,false", "false,false,true", "false,true,false", "false,true,true",
            "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void deliveryProjectsPlatformAndOptionalRestaurantWithoutNegativePending(
            boolean postgres, boolean restaurant, boolean missingAmount) {
        var events = mock(AnalyticsEventRepository.class);
        var orders = mock(DailyOrderStatsRepository.class);
        var revenue = mock(DailyRevenueStatsRepository.class);
        var items = mock(DailyItemSalesRepository.class);
        var service = new EventProcessingService(events, orders, revenue, items);
        Long restaurantId = restaurant ? 7L : null;
        BigDecimal amount = missingAmount ? null : new BigDecimal("15");
        BigDecimal increment = missingAmount ? BigDecimal.ZERO : amount;
        var platform = row(null, 0);
        var shop = row(7L, 2);
        if (postgres) {
            ReflectionTestUtils.setField(service, "dataSourceUrl", "jdbc:postgresql://localhost/analytics");
            when(events.insertIfAbsentPostgres(anyString(), anyString(), any(), any(), any(), any(),
                    any(), any(), any(), anyString(), anyString(), any())).thenReturn(1);
        } else {
            when(orders.findByStatDateAndRestaurantIdIsNull(any())).thenReturn(Optional.of(platform));
            if (restaurant) when(orders.findByStatDateAndRestaurantId(any(), eq(7L)))
                    .thenReturn(Optional.of(shop));
        }

        service.processOrderDelivered(1L, restaurantId, "Shop", amount, "{\"eventId\":\"delivered\"}");

        if (postgres) {
            verify(orders).incrementDeliveredPostgres(any(LocalDate.class), isNull(), eq(increment));
            if (restaurant) verify(orders).incrementDeliveredPostgres(any(LocalDate.class), eq(7L), eq(increment));
            verifyNoMoreInteractions(orders);
        } else {
            verify(orders).save(platform);
            assertThat(platform.getPendingOrders()).isZero();
            assertProjection(platform, increment);
            if (restaurant) {
                verify(orders).save(shop);
                assertThat(shop.getPendingOrders()).isEqualTo(1);
                assertProjection(shop, increment);
            }
        }
        verifyNoInteractions(revenue, items);
    }

    private static void assertProjection(DailyOrderStats row, BigDecimal increment) {
        assertThat(row.getTotalOrders()).isEqualTo(4);
        assertThat(row.getCancelledOrders()).isEqualTo(1);
        assertThat(row.getDeliveredOrders()).isEqualTo(2);
        assertThat(row.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("10").add(increment));
        assertThat(row.getAvgOrderValue()).isEqualByComparingTo(increment.signum() == 0 ? "5" : "13");
    }

    private static DailyOrderStats row(Long restaurantId, long pending) {
        return DailyOrderStats.builder().statDate(LocalDate.now()).restaurantId(restaurantId)
                .totalOrders(4).cancelledOrders(1).deliveredOrders(1).pendingOrders(pending)
                .totalRevenue(BigDecimal.TEN).avgOrderValue(BigDecimal.TEN).build();
    }
}
