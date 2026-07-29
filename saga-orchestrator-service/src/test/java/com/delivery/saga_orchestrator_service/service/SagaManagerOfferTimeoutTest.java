package com.delivery.saga_orchestrator_service.service;

import com.delivery.saga_orchestrator_service.entity.SagaInstance;
import com.delivery.saga_orchestrator_service.repository.SagaInstanceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaManagerOfferTimeoutTest {

    @Mock SagaInstanceRepository repository;
    @Mock SagaOutboxService outboxService;

    @Test
    void timedOutOfferRematchesAndExcludesThePreviousShipper() {
        SagaInstance saga = new SagaInstance();
        saga.setOrderId(10L);
        saga.setDeliveryId(20L);
        saga.setSagaType("ORDER_CREATION");
        saga.setStatus(SagaInstance.SagaStatus.SHIPPER_FOUND);
        saga.setPayload("""
                {"orderId":10,"totalPrice":120000,"shippingFee":20000,
                 "paymentMethod":"COD","restaurantId":40,"restaurantName":"Test"}
                """);
        saga.addStep("DELIVERY_CREATED", "delivery.created.result", """
                {"orderId":10,"deliveryId":20,"pickupLat":10.75,"pickupLng":106.67,
                 "deliveryLat":10.76,"deliveryLng":106.68}
                """);
        saga.addStep("SHIPPER_FOUND", "shipper.found", """
                {"orderId":10,"deliveryId":20,"pickupLat":10.75,"pickupLng":106.67,
                 "foundAt":"2026-07-25T13:00:00","waitingTimeoutSeconds":180,
                 "availableShippers":[{"shipperId":30}]}
                """);
        when(repository.findByOrderIdForUpdate(10L)).thenReturn(Optional.of(saga));
        new SagaManager(repository, outboxService).handleShipperOfferTimeout(10L);

        assertThat(saga.getStatus()).isEqualTo(SagaInstance.SagaStatus.FINDING_SHIPPER);
        assertThat(saga.getSteps()).anyMatch(step -> step.getStepName().startsWith("SHIPPER_OFFER_TIMEOUT_"));
        verify(repository).save(saga);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).saveCommand(eq("10"), eq(SagaManager.CMD_FIND_SHIPPER),
                eq("10"), payload.capture());
        JsonNode command = (JsonNode) payload.getValue();
        assertThat(command.get("excludedShipperIds").get(0).asLong()).isEqualTo(30L);
        assertThat(command.get("totalPrice").decimalValue()).isEqualByComparingTo("120000");
        assertThat(command.get("paymentMethod").asText()).isEqualTo("COD");
        assertThat(command.get("deliveryLat").asDouble()).isEqualTo(10.76);

        ArgumentCaptor<Object> expirePayload = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).saveCommand(eq("10"), eq(SagaManager.CMD_EXPIRE_SHIPPER_OFFER),
                eq("10"), expirePayload.capture());
        JsonNode expire = (JsonNode) expirePayload.getValue();
        assertThat(expire.get("deliveryId").asLong()).isEqualTo(20L);
        assertThat(expire.get("timedOutShipperId").asLong()).isEqualTo(30L);
        assertThat(java.time.LocalDateTime.parse(expire.get("expectedOfferExpiresAt").asText()))
                .isEqualTo(java.time.LocalDateTime.of(2026, 7, 25, 13, 3));
    }

    @Test
    void timeoutCommandsUseRecoveryTopicOverrides() {
        SagaInstance saga = new SagaInstance();
        saga.setOrderId(10L);
        saga.setDeliveryId(20L);
        saga.setSagaType("ORDER_CREATION");
        saga.setStatus(SagaInstance.SagaStatus.SHIPPER_FOUND);
        saga.setPayload("""
                {"orderId":10,"totalPrice":120000,"shippingFee":20000,
                 "paymentMethod":"COD","restaurantId":40}
                """);
        saga.addStep("DELIVERY_CREATED", "delivery.created.result",
                "{\"orderId\":10,\"deliveryId\":20}");
        saga.addStep("SHIPPER_FOUND", "shipper.found", """
                {"orderId":10,"deliveryId":20,"foundAt":"2026-07-25T13:00:00",
                 "waitingTimeoutSeconds":180,"availableShippers":[{"shipperId":30}]}
                """);
        when(repository.findByOrderIdForUpdate(10L)).thenReturn(Optional.of(saga));

        SagaManager manager = new SagaManager(repository, outboxService);
        ReflectionTestUtils.setField(manager, "expireShipperOfferTopic", "b8.delivery.expire");
        ReflectionTestUtils.setField(manager, "findShipperTopic", "b8.match.find");
        ReflectionTestUtils.setField(manager, "updateOrderStatusTopic", "b8.order.status");
        manager.handleShipperOfferTimeout(10L);

        verify(outboxService).saveCommand(eq("10"), eq("b8.delivery.expire"), eq("10"), any());
        verify(outboxService).saveCommand(eq("10"), eq("b8.match.find"), eq("10"), any());
        verify(outboxService).saveCommand(eq("10"), eq("b8.order.status"), eq("10"), any());
    }

    @Test
    void timeoutPollBeforeTheOfferDeadlineIsANoOp() {
        SagaInstance saga = offerSaga(LocalDateTime.now().plusMinutes(1), 120, 30L);
        when(repository.findByOrderIdForUpdate(10L)).thenReturn(Optional.of(saga));

        new SagaManager(repository, outboxService).handleShipperOfferTimeout(10L);

        assertThat(saga.getStatus()).isEqualTo(SagaInstance.SagaStatus.SHIPPER_FOUND);
        assertThat(saga.getSteps()).noneMatch(step ->
                step.getStepName().startsWith("SHIPPER_OFFER_TIMEOUT_"));
        verify(repository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void malformedShipperIdentityFailsClosedWithoutQueuingRematch() {
        SagaInstance saga = offerSaga(LocalDateTime.now().minusMinutes(5), 60, 0L);
        when(repository.findByOrderIdForUpdate(10L)).thenReturn(Optional.of(saga));

        new SagaManager(repository, outboxService).handleShipperOfferTimeout(10L);

        assertThat(saga.getStatus()).isEqualTo(SagaInstance.SagaStatus.FAILED);
        verify(outboxService, never()).saveCommand(eq("10"),
                eq(SagaManager.CMD_EXPIRE_SHIPPER_OFFER), eq("10"), any());
        verify(outboxService, never()).saveCommand(eq("10"),
                eq(SagaManager.CMD_FIND_SHIPPER), eq("10"), any());
        verify(outboxService).saveCommand(eq("10"),
                eq(SagaManager.CMD_MARK_SHIPPER_NOT_FOUND), eq("10"), any());
    }

    private SagaInstance offerSaga(LocalDateTime foundAt, int timeoutSeconds, long shipperId) {
        SagaInstance saga = new SagaInstance();
        saga.setOrderId(10L);
        saga.setDeliveryId(20L);
        saga.setSagaType("ORDER_CREATION");
        saga.setStatus(SagaInstance.SagaStatus.SHIPPER_FOUND);
        saga.setPayload("{\"orderId\":10,\"totalPrice\":120000,\"shippingFee\":20000,"
                + "\"paymentMethod\":\"COD\",\"restaurantId\":40}");
        saga.addStep("DELIVERY_CREATED", "delivery.created.result",
                "{\"orderId\":10,\"deliveryId\":20}");
        saga.addStep("SHIPPER_FOUND", "shipper.found",
                "{\"orderId\":10,\"deliveryId\":20,\"foundAt\":\"" + foundAt
                        + "\",\"waitingTimeoutSeconds\":" + timeoutSeconds
                        + ",\"availableShippers\":[{\"shipperId\":" + shipperId + "}]}");
        return saga;
    }
}
