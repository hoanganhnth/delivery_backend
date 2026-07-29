package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.common.constants.NotificationConstants;
import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.dto.response.NotificationResponse;
import com.delivery.notification_service.mapper.NotificationMapper;
import com.delivery.notification_service.repository.NotificationRepository;
import com.delivery.notification_service.service.FirebaseService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class NotificationDeliveryStatusVocabularyTest {

    @Test
    void canonicalDeliveringStatusProducesSpecificCustomerNotification() {
        NotificationServiceImpl service = spy(new NotificationServiceImpl(
                mock(NotificationRepository.class), mock(NotificationMapper.class),
                mock(FirebaseService.class)));
        doReturn(new NotificationResponse()).when(service).sendNotification(any());

        service.sendDeliveryStatusNotification(7L, 11L, "DELIVERING", "Shipper A");

        ArgumentCaptor<SendNotificationRequest> request =
                ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(service).sendNotification(request.capture());
        assertThat(request.getValue().getType()).isEqualTo(NotificationConstants.DELIVERY_DELIVERING);
        assertThat(request.getValue().getTitle()).isEqualTo("Đơn hàng đang được giao");
        assertThat(request.getValue().getMessage()).contains("Shipper A", "đang trên đường giao hàng");
        assertThat(request.getValue().getDeduplicationKey())
                .isEqualTo("delivery-status:11:DELIVERING:7");
    }

    @Test
    void missingShipperNameProducesGenericMessageWithoutSyntheticName() {
        NotificationServiceImpl service = spy(new NotificationServiceImpl(
                mock(NotificationRepository.class), mock(NotificationMapper.class),
                mock(FirebaseService.class)));
        doReturn(new NotificationResponse()).when(service).sendNotification(any());

        service.sendDeliveryStatusNotification(7L, 11L, "DELIVERED", null);

        ArgumentCaptor<SendNotificationRequest> request =
                ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(service).sendNotification(request.capture());
        assertThat(request.getValue().getType()).isEqualTo(NotificationConstants.DELIVERY_DELIVERED);
        assertThat(request.getValue().getMessage())
                .isEqualTo("Đơn hàng đã được giao thành công")
                .doesNotContain("null", "Shipper");
    }
}
