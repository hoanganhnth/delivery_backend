package com.delivery.analytics_service.scheduler;

import com.delivery.analytics_service.AnalyticsServiceApplication;
import com.delivery.analytics_service.entity.AnalyticsEvent;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = AnalyticsServiceApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:reconciliation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "app.analytics.processing-enabled=false", "spring.kafka.listener.auto-startup=false"
})
@Import(StatsReconciliationTransactionTest.Config.class)
class StatsReconciliationTransactionTest {
    @TestConfiguration
    static class Config {
        @Bean
        StatsReconciliationJob reconciliationUnderTest(AnalyticsEventRepository events,
                                                       DailyOrderStatsRepository stats) {
            return new StatsReconciliationJob(events, stats);
        }
    }

    @Autowired StatsReconciliationJob job;
    @Autowired AnalyticsEventRepository events;
    @MockitoSpyBean DailyOrderStatsRepository stats;

    @BeforeEach
    void seed() {
        stats.deleteAll();
        events.deleteAll();
        events.saveAndFlush(AnalyticsEvent.builder().eventType("ORDER_CREATED")
                .eventTime(LocalDate.now().minusDays(1).atTime(12, 0))
                .restaurantId(7L).rawPayload("{}").deduplicationKey("reconcile-test").build());
    }

    @Test
    void directReconciliationRollsBackPlatformWriteWhenRestaurantWriteFails() {
        failSecondSave();
        assertThatThrownBy(() -> job.reconcileDate(LocalDate.now().minusDays(1)))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("injected write failure");
        assertThat(stats.count()).isZero();
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void scheduledReconciliationPropagatesFailureAndRollsBack() {
        failSecondSave();
        assertThatThrownBy(job::reconcileYesterdayStats)
                .isInstanceOf(RuntimeException.class).hasMessageContaining("injected write failure");
        assertThat(stats.count()).isZero();
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void repeatedReconciliationOverwritesRatherThanAccumulatesAndPreservesRawEvents() {
        LocalDate date = LocalDate.now().minusDays(1);
        job.reconcileDate(date);
        job.reconcileDate(date);
        assertThat(stats.count()).isEqualTo(2);
        assertThat(stats.findAll()).allSatisfy(row -> {
            assertThat(row.getTotalOrders()).isEqualTo(1);
            assertThat(row.getPendingOrders()).isEqualTo(1);
        });
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void repairZeroesExistingRestaurantScopeWithoutAcceptedEventsAndPreservesItsIdentity() {
        LocalDate date = LocalDate.now().minusDays(1);
        var stale = stats.saveAndFlush(com.delivery.analytics_service.entity.DailyOrderStats.builder()
                .statDate(date).restaurantId(99L).totalOrders(20).pendingOrders(20)
                .totalRevenue(new java.math.BigDecimal("500"))
                .totalShippingFee(java.math.BigDecimal.TEN).totalDiscount(java.math.BigDecimal.TEN)
                .avgOrderValue(java.math.BigDecimal.TEN).newCustomers(2).build());

        job.reconcileDate(date);
        job.reconcileDate(date);

        var repaired = stats.findByStatDateAndRestaurantId(date, 99L).orElseThrow();
        assertThat(repaired.getId()).isEqualTo(stale.getId());
        assertThat(repaired.getTotalOrders()).isZero();
        assertThat(repaired.getPendingOrders()).isZero();
        assertThat(repaired.getTotalRevenue()).isEqualByComparingTo("0");
        assertThat(stats.count()).isEqualTo(3);
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void repairEmptyDayZeroesExistingProjectionsWithoutCreatingRowsOrTouchingOtherDates() {
        LocalDate date = LocalDate.now().minusDays(2);
        stats.saveAndFlush(com.delivery.analytics_service.entity.DailyOrderStats.builder()
                .statDate(date).totalOrders(20).pendingOrders(20).build());
        stats.saveAndFlush(com.delivery.analytics_service.entity.DailyOrderStats.builder()
                .statDate(date.plusDays(1)).restaurantId(88L).totalOrders(10).build());

        job.reconcileDate(date);

        assertThat(stats.findByStatDateAndRestaurantIdIsNull(date).orElseThrow().getTotalOrders()).isZero();
        assertThat(stats.findByStatDateAndRestaurantId(date.plusDays(1), 88L).orElseThrow().getTotalOrders())
                .isEqualTo(10);
        assertThat(stats.count()).isEqualTo(2);
        assertThat(events.count()).isEqualTo(1);
    }

    private void failSecondSave() {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("injected write failure");
            return stats.saveAndFlush(invocation.getArgument(0));
        }).when(stats).save(any());
    }
}
