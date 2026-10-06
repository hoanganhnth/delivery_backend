package com.delivery.analytics_service.service;

import com.delivery.analytics_service.AnalyticsServiceApplication;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyItemSalesRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real host transactions around the framework-free ingestion use case, without Docker. */
@SpringBootTest(classes = AnalyticsServiceApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:analytics_ingestion_slice2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "app.analytics.processing-enabled=false", "spring.kafka.listener.auto-startup=false"
})
class AnalyticsIngestionTransactionTest {
    @Autowired EventProcessingService service;
    @Autowired AnalyticsEventRepository events;
    @Autowired DailyItemSalesRepository items;
    @MockitoSpyBean DailyOrderStatsRepository orders;
    @MockitoSpyBean DailyRevenueStatsRepository revenue;

    @BeforeEach
    void clearRows() {
        items.deleteAll();
        orders.deleteAll();
        revenue.deleteAll();
        events.deleteAll();
    }

    @Test
    void invalidSecondItemRollsBackReceiptAndOrderCountersThenCorrectedRetryAppliesOnce() {
        assertThatThrownBy(() -> created(snapshot("9")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("analytics item line total does not reconcile");
        assertThat(events.count()).isZero();
        assertThat(orders.count()).isZero();
        assertThat(items.count()).isZero();

        created(snapshot("10"));
        created(snapshot("10"));

        assertThat(events.count()).isEqualTo(1);
        assertThat(orders.count()).isEqualTo(2);
        assertThat(orders.findAll()).allSatisfy(row -> {
            assertThat(row.getTotalOrders()).isEqualTo(1);
            assertThat(row.getPendingOrders()).isEqualTo(1);
        });
        assertThat(items.count()).isEqualTo(2);
        assertThat(items.findAll()).allSatisfy(row -> {
            assertThat(row.getOrderedQuantity()).isEqualTo(1);
            assertThat(row.getOrderedRevenue()).isEqualByComparingTo("10.00");
        });
    }

    @Test
    void restaurantWriteFailureRollsBackAcceptedReceiptAndPlatformProjection() {
        AtomicInteger saves = new AtomicInteger();
        doAnswer(call -> {
            if (saves.incrementAndGet() == 2) throw new IllegalStateException("restaurant write failed");
            return orders.saveAndFlush(call.getArgument(0));
        }).when(orders).save(any());

        assertThatThrownBy(() -> service.processOrderDelivered(1L, 7L, "Shop", BigDecimal.TEN,
                "{\"eventId\":\"delivered\"}"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("restaurant write failed");
        assertThat(events.count()).isZero();
        assertThat(orders.count()).isZero();
    }

    @Test
    void paymentWriteFailureRollsBackReceiptAndAllowsSameKeyRetry() {
        doThrow(new IllegalStateException("payment write failed")).when(revenue).save(any());
        assertThatThrownBy(this::payment).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("payment write failed");
        assertThat(events.count()).isZero();
        assertThat(revenue.count()).isZero();

        doAnswer(call -> revenue.saveAndFlush(call.getArgument(0))).when(revenue).save(any());
        payment();
        payment();
        assertThat(events.count()).isEqualTo(1);
        assertThat(revenue.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getSuccessfulPayments()).isEqualTo(1);
            assertThat(row.getTotalPaymentAmount()).isEqualByComparingTo("12.5");
        });
    }

    private void payment() {
        service.processPaymentCompleted(1L, 2L, 12.5, "COD", "{\"eventId\":\"payment\"}");
    }

    private void created(String payload) {
        service.processOrderCreated(1L, 2L, 7L, "Shop", new BigDecimal("20"), "COD", payload);
    }

    private String snapshot(String secondTotal) {
        return "{\"eventId\":\"created\",\"items\":["
                + "{\"menuItemId\":9,\"quantity\":1,\"unitPrice\":10},"
                + "{\"menuItemId\":10,\"quantity\":1,\"unitPrice\":10,\"lineTotal\":" + secondTotal + "}]}";
    }
}
