package com.delivery.analytics_service.service;

import com.delivery.analytics_service.entity.AnalyticsEvent;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyItemSalesRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventProcessingServiceItemProjectionTest {

    @Test
    void createdAndCancelledSnapshotsIncrementSeparateItemCountersUsingEventDate() {
        AnalyticsEventRepository events = mock(AnalyticsEventRepository.class);
        DailyOrderStatsRepository orders = mock(DailyOrderStatsRepository.class);
        DailyRevenueStatsRepository revenue = mock(DailyRevenueStatsRepository.class);
        DailyItemSalesRepository items = mock(DailyItemSalesRepository.class);
        EventProcessingService service = service(events, orders, revenue, items);
        UUID createdId = UUID.randomUUID();
        UUID cancelledId = UUID.randomUUID();
        String created = payload(createdId, "ORDER_CREATED", "2026-08-20T10:15:00", 2, "35000", "70000");
        String cancelled = payload(cancelledId, "ORDER_CANCELLED", "2026-08-20T11:15:00", 2, "35000", "70000");

        when(events.findByDeduplicationKey("ORDER_CREATED:event:" + createdId)).thenReturn(Optional.empty());
        when(events.findByDeduplicationKey("ORDER_CANCELLED:event:" + cancelledId)).thenReturn(Optional.empty());
        when(items.findByStatDateAndRestaurantIdAndMenuItemId(LocalDate.of(2026, 8, 20), 7L, 9L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(com.delivery.analytics_service.entity.DailyItemSales.builder()
                        .statDate(LocalDate.of(2026, 8, 20)).restaurantId(7L).menuItemId(9L)
                        .menuItemName("Bún bò").orderedQuantity(2).cancelledQuantity(0)
                        .orderedRevenue(new BigDecimal("70000.00"))
                        .cancelledRevenue(BigDecimal.ZERO).updatedAt(java.time.LocalDateTime.now()).build()));

        service.processOrderCreated(101L, 3L, 7L, "Shop", new BigDecimal("120000"), "COD", created);
        service.processOrderCancelled(101L, 7L, cancelled);

        ArgumentCaptor<com.delivery.analytics_service.entity.DailyItemSales> captured =
                ArgumentCaptor.forClass(com.delivery.analytics_service.entity.DailyItemSales.class);
        verify(items, times(2)).save(captured.capture());
        assertThat(captured.getAllValues()).anySatisfy(row -> {
            assertThat(row.getStatDate()).isEqualTo(LocalDate.of(2026, 8, 20));
            assertThat(row.getRestaurantId()).isEqualTo(7L);
            assertThat(row.getMenuItemId()).isEqualTo(9L);
            assertThat(row.getOrderedQuantity()).isEqualTo(2);
            assertThat(row.getCancelledQuantity()).isEqualTo(0);
            assertThat(row.getOrderedRevenue()).isEqualByComparingTo("70000.00");
        });
        assertThat(captured.getAllValues()).anySatisfy(row -> {
            assertThat(row.getCancelledQuantity()).isEqualTo(2);
            assertThat(row.getCancelledRevenue()).isEqualByComparingTo("70000.00");
        });
    }

    @Test
    void duplicateWholeEventSkipsItemMutation() {
        AnalyticsEventRepository events = mock(AnalyticsEventRepository.class);
        DailyOrderStatsRepository orders = mock(DailyOrderStatsRepository.class);
        DailyRevenueStatsRepository revenue = mock(DailyRevenueStatsRepository.class);
        DailyItemSalesRepository items = mock(DailyItemSalesRepository.class);
        EventProcessingService service = service(events, orders, revenue, items);
        UUID eventId = UUID.randomUUID();
        String payload = payload(eventId, "ORDER_CREATED", "2026-08-20T10:15:00", 1, "10000", "9999");
        when(events.findByDeduplicationKey("ORDER_CREATED:event:" + eventId))
                .thenReturn(Optional.of(AnalyticsEvent.builder()
                        .deduplicationKey("ORDER_CREATED:event:" + eventId).eventType("ORDER_CREATED")
                        .orderId(101L).userId(3L).restaurantId(7L).restaurantName("Shop")
                        .amount(new BigDecimal("120000")).orderStatus("PENDING").paymentMethod("COD")
                        .rawPayload(payload).payloadFingerprint(null).build()));

        service.processOrderCreated(101L, 3L, 7L, "Shop",
                new BigDecimal("120000"), "COD", payload);
        verify(items, never()).save(any());
    }

    @Test
    void malformedLineFailsClosedBeforeItemMutation() {
        AnalyticsEventRepository events = mock(AnalyticsEventRepository.class);
        DailyOrderStatsRepository orders = mock(DailyOrderStatsRepository.class);
        DailyRevenueStatsRepository revenue = mock(DailyRevenueStatsRepository.class);
        DailyItemSalesRepository items = mock(DailyItemSalesRepository.class);
        EventProcessingService service = service(events, orders, revenue, items);
        UUID eventId = UUID.randomUUID();
        String payload = payload(eventId, "ORDER_CREATED", "2026-08-20T10:15:00", 1, "10000", "9999");
        when(events.findByDeduplicationKey("ORDER_CREATED:event:" + eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processOrderCreated(101L, 3L, 7L, "Shop",
                new BigDecimal("120000"), "COD", payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line total");
        verify(items, never()).save(any());
    }

    private EventProcessingService service(AnalyticsEventRepository events,
                                           DailyOrderStatsRepository orders,
                                           DailyRevenueStatsRepository revenue,
                                           DailyItemSalesRepository items) {
        EventProcessingService service = new EventProcessingService(events, orders, revenue, items);
        ReflectionTestUtils.setField(service, "dataSourceUrl", "jdbc:h2:mem:analytics-items");
        return service;
    }

    private String payload(UUID eventId, String eventType, String occurredAt,
                           int quantity, String unitPrice, String lineTotal) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType
                + "\",\"occurredAt\":\"" + occurredAt
                + "\",\"orderId\":101,\"restaurantId\":7,\"items\":[{"
                + "\"orderItemId\":1001,\"menuItemId\":9,\"menuItemName\":\"Bún bò\","
                + "\"quantity\":" + quantity + ",\"unitPrice\":" + unitPrice
                + ",\"lineTotal\":" + lineTotal + "}]}";
    }
}
