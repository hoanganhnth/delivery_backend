package com.delivery.analytics_service.service;
import com.delivery.analytics_service.entity.*;
import com.delivery.analytics_service.listener.PaymentEventListener;
import com.delivery.analytics_service.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.kafka.support.Acknowledgment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AnalyticsPreservedLimitationsTest {
    @Test void paymentDoubleConversionStillLosesSourcePrecisionBeforeReceiptAndProjection() {
        var events=mock(AnalyticsEventRepository.class);
        var revenue=mock(DailyRevenueStatsRepository.class);
        var service=new EventProcessingService(events,mock(DailyOrderStatsRepository.class),revenue);
        var ack=mock(Acknowledgment.class);
        new PaymentEventListener(service).onPaymentCompleted("{\"orderId\":1,\"amount\":123.123456789123456789}",ack);
        var receipt=ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(events).saveAndFlush(receipt.capture());
        assertThat(receipt.getValue().getAmount()).isEqualByComparingTo("123.12345678912345");
        var projection=ArgumentCaptor.forClass(DailyRevenueStats.class);
        verify(revenue).save(projection.capture());
        assertThat(projection.getValue().getTotalPaymentAmount()).isEqualByComparingTo(receipt.getValue().getAmount());
        verify(ack).acknowledge();
    }
    @Test void itemEventDayAndOrderProcessingDayRemainDifferentAndVersionIsNotOrderingFence() {
        var events=mock(AnalyticsEventRepository.class);var orders=mock(DailyOrderStatsRepository.class);
        var items=mock(DailyItemSalesRepository.class);
        when(events.insertIfAbsentPostgres(anyString(),anyString(),any(),any(),any(),any(),any(),any(),any(),anyString(),anyString(),any())).thenReturn(1);
        var service=new EventProcessingService(events,orders,mock(DailyRevenueStatsRepository.class),items);
        ReflectionTestUtils.setField(service,"dataSourceUrl","jdbc:postgresql://localhost/analytics");
        for(int version:new int[]{9,1}) service.processOrderCreated(1L,2L,7L,"Shop",BigDecimal.TEN,"COD",
                "{\"eventId\":\"version-"+version+"\",\"aggregateVersion\":"+version+",\"occurredAt\":\"2025-01-02T03:04:05\","
                +"\"items\":[{\"menuItemId\":9,\"quantity\":1,\"unitPrice\":10}]}");
        verify(orders,times(2)).incrementCreatedPostgres(LocalDate.now(),null);
        verify(orders,times(2)).incrementCreatedPostgres(LocalDate.now(),7L);
        verify(items,times(2)).incrementPostgres(eq(LocalDate.of(2025,1,2)),eq(7L),eq(9L),eq("UNKNOWN"),eq(1L),eq(0L),
                eq(new BigDecimal("10.00")),eq(BigDecimal.ZERO),any());
    }
    @Test void absentRestaurantSkipsMalformedItemsButStillValidatesPresentTimestamp() {
        var events=mock(AnalyticsEventRepository.class);var orders=mock(DailyOrderStatsRepository.class);var items=mock(DailyItemSalesRepository.class);
        var service=new EventProcessingService(events,orders,mock(DailyRevenueStatsRepository.class),items);
        service.processOrderCreated(1L,2L,null,null,BigDecimal.TEN,"COD","{\"items\":false}");
        verifyNoInteractions(items);verify(orders).save(any());
        assertThatThrownBy(()->service.processOrderCreated(2L,2L,null,null,BigDecimal.TEN,"COD",
                "{\"occurredAt\":\"invalid\",\"items\":false}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("analytics event timestamp is invalid");
        verify(orders,times(1)).save(any());
    }
}
