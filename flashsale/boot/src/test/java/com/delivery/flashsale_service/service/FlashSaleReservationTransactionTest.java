package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.FlashSaleReservationRequest;
import com.delivery.flashsale_service.dto.ReserveItemRequest;
import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import com.delivery.flashsale_service.entity.FlashSaleItem;
import com.delivery.flashsale_service.entity.FlashSaleReservation;
import com.delivery.flashsale_service.repository.FlashSaleCampaignRepository;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import com.delivery.flashsale_service.repository.FlashSaleOutboxEventRepository;
import com.delivery.flashsale_service.repository.FlashSaleReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flashsale-transaction;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "app.flashsale.checkout-enabled=true", "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FlashSaleStockService.class, FlashSaleOutboxService.class, FlashSaleCronService.class,
        FlashSaleReservationTransactionTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FlashSaleReservationTransactionTest {
    @TestConfiguration
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }

    @Autowired FlashSaleStockService service;
    @Autowired FlashSaleCronService cron;
    @Autowired FlashSaleItemRepository items;
    @Autowired FlashSaleCampaignRepository campaigns;
    @Autowired FlashSaleReservationRepository reservations;
    @MockitoSpyBean FlashSaleOutboxEventRepository outbox;
    private FlashSaleReservationRequest request;
    private Long itemId;

    @BeforeEach
    void seed() {
        outbox.deleteAll();
        reservations.deleteAll();
        items.deleteAll();
        campaigns.deleteAll();
        var campaign = campaigns.saveAndFlush(FlashSaleCampaign.builder().name("All day")
                .isRecurring(false).startTime(LocalTime.MIN).endTime(LocalTime.of(23, 59, 59))
                .status(FlashSaleCampaign.CampaignStatus.ACTIVE).adminId(1L).build());
        itemId = items.saveAndFlush(FlashSaleItem.builder().campaign(campaign).restaurantId(9L)
                .menuItemId(91L).originalPrice(new BigDecimal("100000"))
                .flashSalePrice(new BigDecimal("50000")).stockQuantity(2).soldQuantity(0)
                .status(FlashSaleItem.ItemStatus.APPROVED).build()).getId();
        var line = new ReserveItemRequest();
        line.setFlashSaleItemId(itemId);
        line.setQuantity(1);
        request = new FlashSaleReservationRequest();
        request.setReservationId(UUID.randomUUID());
        request.setOrderId(101L);
        request.setUserId(7L);
        request.setUserPrincipalId(70L);
        request.setRestaurantId(9L);
        request.setItems(List.of(line));
    }

    @Test
    void reserveOutboxFailureRollsBackFlushedReservationAndStockThenAllowsRetry() {
        failNextOutboxWrite();

        assertThatThrownBy(() -> service.reserveStock(request)).hasMessageContaining("injected outbox failure");
        assertThat(reservations.count()).isZero();
        assertThat(outbox.count()).isZero();
        assertThat(items.findById(itemId).orElseThrow().getSoldQuantity()).isZero();

        service.reserveStock(request);
        service.reserveStock(request);
        assertThat(reservations.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(items.findById(itemId).orElseThrow().getSoldQuantity()).isEqualTo(1);
    }

    @Test
    void failedCommitKeepsReservedStateAndRetryPublishesOnce() {
        service.reserveStock(request);
        failNextOutboxWrite();

        assertThatThrownBy(() -> service.commit(request.getReservationId(), 101L))
                .hasMessageContaining("injected outbox failure");
        assertStored(FlashSaleReservation.State.RESERVED, 1, 1);

        service.commit(request.getReservationId(), 101L);
        service.commit(request.getReservationId(), 101L);
        assertStored(FlashSaleReservation.State.COMMITTED, 1, 2);
    }

    @Test
    void failedReleaseRestoresNeitherCapacityNorStateAndRetryReturnsStockOnce() {
        service.reserveStock(request);
        service.commit(request.getReservationId(), 101L);
        failNextOutboxWrite();

        assertThatThrownBy(() -> service.release(request.getReservationId(), 101L))
                .hasMessageContaining("injected outbox failure");
        assertStored(FlashSaleReservation.State.COMMITTED, 1, 2);

        service.release(request.getReservationId(), 101L);
        service.release(request.getReservationId(), 101L);
        assertStored(FlashSaleReservation.State.RELEASED, 0, 3);
    }

    private void assertStored(FlashSaleReservation.State state, int sold, long eventCount) {
        assertThat(reservations.findById(request.getReservationId()).orElseThrow().getState()).isEqualTo(state);
        assertThat(items.findById(itemId).orElseThrow().getSoldQuantity()).isEqualTo(sold);
        assertThat(outbox.count()).isEqualTo(eventCount);
    }

    @Test
    void recurringResetStillZerosDurableHoldsAndLaterReleaseFailsLedgerCheck() {
        var campaign = campaigns.findAll().get(0);
        campaign.setIsRecurring(true);
        campaigns.saveAndFlush(campaign);
        service.reserveStock(request);

        cron.resetRecurringCampaignStock();

        assertStored(FlashSaleReservation.State.RESERVED, 0, 1);
        assertThatThrownBy(() -> service.release(request.getReservationId(), request.getOrderId()))
                .hasMessage("Flash sale stock ledger is inconsistent");
        assertStored(FlashSaleReservation.State.RESERVED, 0, 1);
    }

    @Test
    void recurringResetStillIncludesDeletedApprovedItems() {
        var campaign = campaigns.findAll().get(0);
        campaign.setIsRecurring(true);
        campaigns.saveAndFlush(campaign);
        var item = items.findById(itemId).orElseThrow();
        item.setSoldQuantity(1);
        item.setDeletedAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        items.saveAndFlush(item);

        cron.resetRecurringCampaignStock();

        var stored = items.findById(itemId).orElseThrow();
        assertThat(stored.getSoldQuantity()).isZero();
        assertThat(stored.getDeletedAt()).isEqualTo(item.getDeletedAt());
    }

    private void failNextOutboxWrite() {
        AtomicBoolean failure = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (failure.getAndSet(false)) throw new IllegalStateException("injected outbox failure");
            return outbox.saveAndFlush(invocation.getArgument(0));
        }).when(outbox).save(any());
    }
}
