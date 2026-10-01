package com.delivery.analytics_service.service;

import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyItemSalesRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EventProcessingServicePayloadValidationTest {
    @Mock AnalyticsEventRepository events;
    @Mock DailyOrderStatsRepository orders;
    @Mock DailyRevenueStatsRepository revenue;
    @Mock DailyItemSalesRepository items;

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "17", "\"payload\"", "{broken"})
    void rejectsNonObjectPayloadBeforeReceiptOrProjection(String payload) {
        var service = new EventProcessingService(events, orders, revenue, items);

        assertThatThrownBy(() -> service.processOrderCreated(9L, 2L, 3L, "Restaurant",
                BigDecimal.TEN, "COD", payload)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(events, orders, revenue, items);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "0", "-1", "9223372036854775808", "\"17\""})
    void rejectsNonPositiveOrNonIntegralVersionBeforeReceipt(String version) {
        var service = new EventProcessingService(events, orders, revenue, items);
        String payload = "{\"eventId\":\"versioned\",\"aggregateVersion\":" + version + "}";

        assertThatThrownBy(() -> service.processOrderCreated(9L, 2L, 3L, "Restaurant",
                BigDecimal.TEN, "COD", payload)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateVersion");

        verifyNoInteractions(events, orders, revenue, items);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"menuItemId\":9.5,\"quantity\":1", "\"menuItemId\":9,\"quantity\":1.5"})
    void fractionalItemIdentityOrQuantityCannotBeTruncatedIntoAnAcceptedSnapshot(String fields) {
        var service = new EventProcessingService(events, orders, revenue, items);
        String payload = "{\"eventId\":\"fractional\",\"items\":[{" + fields
                + ",\"unitPrice\":10,\"lineTotal\":10}]}";

        assertThatThrownBy(() -> service.processOrderCreated(9L, 2L, 3L, "Restaurant",
                BigDecimal.TEN, "COD", payload)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");

        verifyNoInteractions(items);
    }
}
