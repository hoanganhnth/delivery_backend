package com.delivery.analytics_service.service;

import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyItemSalesRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalyticsEventDateTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "\"occurredAt\":\"2025-01-02T03:04:05\"",
            "\"eventTimestamp\":\"2025-01-02T03:04:05\"",
            "\"createdAt\":\"2025-01-02T03:04:05\"",
            "\"occurredAt\":null,\"eventTimestamp\":\"2025-01-02T03:04:05\"",
            "\"occurredAt\":\"   \",\"createdAt\":\"2025-01-02T03:04:05\"",
            "\"occurredAt\":\"2025-01-02T03:04:05\",\"createdAt\":\"2024-01-01T00:00:00\""
    })
    void projectsItemsUsingFirstPresentEventTimestamp(String timestamps) {
        var items = mock(DailyItemSalesRepository.class);
        var service = service(items);
        service.processOrderCreated(1L, 2L, 3L, "Shop", BigDecimal.TEN, "COD", payload(timestamps));
        verify(items).incrementPostgres(eq(LocalDate.of(2025, 1, 2)), eq(3L), eq(9L),
                anyString(), eq(1L), eq(0L), eq(new BigDecimal("10.00")), eq(BigDecimal.ZERO), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"oops", "2025-02-30T00:00:00", "2025-01-02"})
    void rejectsInvalidPresentTimestampInsteadOfFallingBack(String timestamp) {
        var items = mock(DailyItemSalesRepository.class);
        var service = service(items);
        assertThatThrownBy(() -> service.processOrderCreated(1L, 2L, 3L, "Shop", BigDecimal.TEN,
                "COD", payload("\"occurredAt\":\"" + timestamp
                        + "\",\"createdAt\":\"2025-01-02T03:04:05\"")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timestamp is invalid");
        verifyNoInteractions(items);
    }

    private EventProcessingService service(DailyItemSalesRepository items) {
        var events = mock(AnalyticsEventRepository.class);
        when(events.insertIfAbsentPostgres(anyString(), anyString(), any(), any(), any(), any(),
                any(), any(), any(), anyString(), anyString(), any())).thenReturn(1);
        var service = new EventProcessingService(events, mock(DailyOrderStatsRepository.class),
                mock(DailyRevenueStatsRepository.class), items);
        ReflectionTestUtils.setField(service, "dataSourceUrl", "jdbc:postgresql://localhost/analytics");
        return service;
    }

    private String payload(String timestamps) {
        return "{\"eventId\":\"date-test\"," + timestamps
                + ",\"items\":[{\"menuItemId\":9,\"quantity\":1,\"unitPrice\":10}]}";
    }
}
