package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.entity.Notification;
import com.delivery.notification_service.mapper.NotificationMapper;
import com.delivery.notification_service.repository.NotificationRepository;
import com.delivery.notification_service.service.FirebaseService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import com.delivery.notification_service.common.constants.NotificationConstants;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class NotificationServiceDeduplicationTest {

    @Test
    void orderAndDeliveryConsumersUseStableEventIdsAsTheirDeduplicationKeys() {
        NotificationServiceImpl service = spy(new NotificationServiceImpl(
                mock(NotificationRepository.class), new NotificationMapper(), mock(FirebaseService.class)));
        doReturn(null).when(service).sendNotification(any());
        UUID orderEventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID deliveryEventId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        service.sendOrderCreatedNotification(orderEventId, 42L, 7L, "Restaurant A");
        service.sendDeliveryStatusNotification(deliveryEventId, 42L, 9L, "DELIVERING", null);

        ArgumentCaptor<SendNotificationRequest> requests = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(service, times(2)).sendNotification(requests.capture());
        org.assertj.core.api.Assertions.assertThat(requests.getAllValues())
                .extracting(SendNotificationRequest::getDeduplicationKey)
                .containsExactly("order-created:" + orderEventId, "delivery-status:" + deliveryEventId);
    }

    @Test
    void separateOfferEventsForSameOrderAndShipperUseSeparateDeduplicationKeys() {
        NotificationServiceImpl service = spy(new NotificationServiceImpl(
                mock(NotificationRepository.class), new NotificationMapper(),
                mock(FirebaseService.class)));
        doReturn(null).when(service).sendNotification(any());

        service.sendShipperMatchFoundNotification(5L, 7L, "R", "P", "D", 1.2, "offer-event-1");
        service.sendShipperMatchFoundNotification(5L, 7L, "R", "P", "D", 1.2, "offer-event-2");

        ArgumentCaptor<SendNotificationRequest> requests = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(service, times(2)).sendNotification(requests.capture());
        org.assertj.core.api.Assertions.assertThat(requests.getAllValues())
                .extracting(SendNotificationRequest::getDeduplicationKey)
                .containsExactly("shipper-offer:offer-event-1:5", "shipper-offer:offer-event-2:5");
        org.assertj.core.api.Assertions.assertThat(requests.getAllValues())
                .allSatisfy(request -> {
                    org.assertj.core.api.Assertions.assertThat(request.getSendPush()).isTrue();
                    org.assertj.core.api.Assertions.assertThat(request.getMessage()).doesNotContain("Phí:");
                    org.assertj.core.api.Assertions.assertThat(request.getData())
                            .contains("/api/deliveries/offers/current")
                            .doesNotContain("estimatedPrice", "estimatedTime");
                });
    }

    @Test
    void replayReturnsStoredNotificationWithoutSendingAgain() {
        NotificationRepository repository = mock(NotificationRepository.class);
        FirebaseService firebaseService = mock(FirebaseService.class);
        NotificationServiceImpl service = new NotificationServiceImpl(
                repository, new NotificationMapper(), firebaseService);

        Notification existing = new Notification();
        existing.setId(9L);
        existing.setUserId(42L);
        existing.setTitle("Existing");
        existing.setMessage("Existing message");
        existing.setType("ORDER_CREATED");
        existing.setStatus(NotificationConstants.STATUS_SENT);
        existing.setDeduplicationKey("order-created:7:42");
        when(repository.findByDeduplicationKey("order-created:7:42"))
                .thenReturn(Optional.of(existing));

        SendNotificationRequest request = completeRequest();

        service.sendNotification(request);

        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(firebaseService);
    }

    @Test
    void replayWithSameKeyButDifferentPayloadIsRejectedWithoutDisclosureOrDelivery() {
        NotificationRepository repository = mock(NotificationRepository.class);
        FirebaseService firebaseService = mock(FirebaseService.class);
        NotificationServiceImpl service = new NotificationServiceImpl(
                repository, new NotificationMapper(), firebaseService);

        Notification existing = storedPendingNotification();
        when(repository.findByDeduplicationKey("order-created:7:42"))
                .thenReturn(Optional.of(existing));
        SendNotificationRequest request = completeRequest();
        request.setUserId(99L);

        assertThrows(com.delivery.notification_service.exception.NotificationConflictException.class,
                () -> service.sendNotification(request));

        verify(repository, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(firebaseService);
    }

    @Test
    void pendingReplayUsesStableNotificationIdAndCompletesDelivery() {
        NotificationRepository repository = mock(NotificationRepository.class);
        FirebaseService firebaseService = mock(FirebaseService.class);
        NotificationServiceImpl service = new NotificationServiceImpl(
                repository, new NotificationMapper(), firebaseService);

        Notification existing = storedPendingNotification();
        when(repository.findByDeduplicationKey("order-created:7:42"))
                .thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(existing));
        SendNotificationRequest request = completeRequest();

        var response = service.sendNotification(request);

        verify(repository, never()).saveAndFlush(any());
        verify(firebaseService).sendPushNotificationToUser(eq(42L), anyString(), anyString(), anyMap());
        verify(repository).save(existing);
        org.junit.jupiter.api.Assertions.assertEquals(9L, response.getId());
        org.junit.jupiter.api.Assertions.assertEquals(NotificationConstants.STATUS_SENT, response.getStatus());
    }

    @Test
    void configuredPushFailureLeavesPersistedPendingRecordForKafkaRetry() {
        NotificationRepository repository = mock(NotificationRepository.class);
        FirebaseService firebaseService = mock(FirebaseService.class);
        NotificationServiceImpl service = new NotificationServiceImpl(
                repository, new NotificationMapper(), firebaseService);
        Notification pending = storedPendingNotification();
        when(repository.findByDeduplicationKey("order-created:7:42"))
                .thenReturn(Optional.empty(), Optional.of(pending));
        when(repository.insertIfAbsentPostgres(
                anyLong(), isNull(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(),
                any(), any(), any(), anyString())).thenReturn(1);
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(storedPendingNotification()));
        doThrow(new IllegalStateException("firebase unavailable"))
                .when(firebaseService).sendPushNotificationToUser(
                        eq(42L), anyString(), anyString(), anyMap());

        assertThrows(IllegalStateException.class, () -> service.sendNotification(completeRequest()));

        verify(repository).insertIfAbsentPostgres(
                eq(42L), isNull(), eq("Existing"), eq("Existing message"), eq("ORDER_CREATED"), eq("MEDIUM"),
                eq(NotificationConstants.STATUS_PENDING), eq(false), isNull(), isNull(), isNull(),
                eq("order-created:7:42"));
        verify(repository, never()).save(argThat(notification ->
                NotificationConstants.STATUS_SENT.equals(notification.getStatus())));
    }

    @Test
    void concurrentWaiterSkipsDeliveryAfterLockedRowIsAlreadySent() {
        NotificationRepository repository = mock(NotificationRepository.class);
        FirebaseService firebaseService = mock(FirebaseService.class);
        Notification existing = storedPendingNotification();
        existing.setStatus(NotificationConstants.STATUS_SENT);
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(existing));
        NotificationDeliveryCoordinator coordinator = new NotificationDeliveryCoordinator(
                repository, firebaseService);

        coordinator.deliverPending(completeRequest(), new NotificationMapper().toResponse(existing));

        verifyNoInteractions(firebaseService);
        verify(repository, never()).save(any());
    }

    private static Notification storedPendingNotification() {
        Notification existing = new Notification();
        existing.setId(9L);
        existing.setUserId(42L);
        existing.setTitle("Existing");
        existing.setMessage("Existing message");
        existing.setType("ORDER_CREATED");
        existing.setPriority("MEDIUM");
        existing.setStatus(NotificationConstants.STATUS_PENDING);
        existing.setDeduplicationKey("order-created:7:42");
        return existing;
    }

    private static SendNotificationRequest completeRequest() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(42L);
        request.setTitle("Existing");
        request.setMessage("Existing message");
        request.setType("ORDER_CREATED");
        request.setPriority("MEDIUM");
        request.setDeduplicationKey("order-created:7:42");
        return request;
    }
}
