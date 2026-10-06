package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.exception.NotificationConflictException;
import com.delivery.notification_service.entity.Notification;
import com.delivery.notification_service.mapper.NotificationMapper;
import com.delivery.notification_service.repository.NotificationRepository;
import com.delivery.notification_service.service.FirebaseService;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Golden expectations from the pre-extraction host, including serialized replay identity. */
class NotificationMappingEquivalenceTest {
    private static final UUID EVENT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void orderRequestPreservesAllFieldsAndLegacyOverload() {
        var service = mappingService();
        service.sendOrderCreatedNotification(EVENT, 7L, 71L, 9L, " R ");
        var request = captured(service);
        assertCommon(request, 7L, 71L, "Đơn hàng đã được tạo",
                "Đơn hàng #9 từ  R  đã được tạo thành công", "ORDER_CREATED", "MEDIUM", 9L,
                "ORDER", "order-created:" + EVENT);
        assertNull(request.getData());
        service = mappingService();
        service.sendOrderCreatedNotification(EVENT, 7L, 9L, "R");
        assertNull(captured(service).getUserPrincipalId());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "PENDING|Đơn đang chờ xử lý giao hàng|Đơn hàng đang chờ bắt đầu quy trình giao",
        "FINDING_SHIPPER|Đang tìm shipper|Hệ thống đang tìm shipper cho đơn hàng của bạn",
        "WAIT_SHIPPER_CONFIRM|Đang chờ shipper xác nhận|Đang chờ shipper xác nhận nhận đơn",
        "SHIPPER_NOT_FOUND|Chưa tìm được shipper|Hiện chưa tìm được shipper phù hợp cho đơn hàng",
        "ASSIGNED|Đã phân công shipper|Đơn hàng của bạn đã được phân công cho shipper",
        "PICKED_UP|Shipper đã lấy hàng|Đơn hàng của bạn đã được lấy và chuẩn bị giao",
        "DELIVERING|Đơn hàng đang được giao|Đơn hàng của bạn đang được giao",
        "DELIVERED|Giao hàng hoàn thành|Đơn hàng đã được giao thành công",
        "CANCELLED|Giao hàng đã bị hủy|Quy trình giao hàng đã bị hủy"
    })
    void deliveryRequestPreservesEveryStatus(String status, String title, String message) {
        var service = mappingService();
        service.sendDeliveryStatusNotification(EVENT, 7L, 71L, 11L, status, null);
        var request = captured(service);
        assertCommon(request, 7L, 71L, title, message, "DELIVERY_" + status, "HIGH", 11L,
                "DELIVERY", "delivery-status:" + EVENT);
        assertNull(request.getData());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"P"})
    void offerRequestPreservesExactGsonBytesIncludingNullOmission(String pickup) {
        var service = mappingService();
        service.sendShipperMatchFoundNotification(5L, 9L, "R", pickup, "D<>&", 1.25, "offer-1");
        var request = captured(service);
        assertCommon(request, 5L, null, "🎯 Đơn hàng phù hợp!",
                String.format("Đơn hàng #%d từ %s - cách điểm lấy khoảng %.1fkm. Mở ứng dụng để xem offer hiện tại.",
                        9L, "R", 1.25), "MATCH_FOUND", "HIGH", 9L, "ORDER", "shipper-offer:offer-1:5");
        // Original insertion and serialization, independent of the new domain intent.
        Map<String, Object> legacyData = new HashMap<>();
        legacyData.put("pickupAddress", pickup);
        legacyData.put("deliveryAddress", "D<>&");
        legacyData.put("distance", 1.25);
        legacyData.put("orderId", 9L);
        legacyData.put("recoveryEndpoint", "/api/deliveries/offers/current");
        assertEquals(new Gson().toJson(legacyData), request.getData());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETURNING", "RETURNED", "UNKNOWN"})
    void listenerAcceptedReturnStatusesRemainUnmapped(String status) {
        var service = mappingService();
        assertEquals("Unknown delivery status: " + status, assertThrows(IllegalArgumentException.class,
                () -> service.sendDeliveryStatusNotification(EVENT, 7L, 11L, status, null)).getMessage());
        verify(service, never()).sendNotification(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UserId", "UserPrincipalId", "Title", "Message", "Type", "Priority",
            "RelatedEntityId", "RelatedEntityType", "Data"})
    void everyReplayMismatchRetainsHostConflictExceptionBeforeDelivery(String field) throws Exception {
        NotificationRepository repository = mock(NotificationRepository.class);
        FirebaseService firebase = mock(FirebaseService.class);
        var service = new NotificationServiceImpl(repository, new NotificationMapper(), firebase);
        Notification stored = new Notification();
        stored.setUserId(7L); stored.setUserPrincipalId(71L); stored.setTitle("title");
        stored.setMessage("message"); stored.setType("type"); stored.setPriority("HIGH");
        stored.setRelatedEntityId(9L); stored.setRelatedEntityType("ORDER"); stored.setData("{}");
        stored.setStatus("SENT");
        when(repository.findByDeduplicationKey("key")).thenReturn(Optional.of(stored));
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(7L); request.setUserPrincipalId(71L); request.setTitle("title");
        request.setMessage("message"); request.setType("type"); request.setPriority("HIGH");
        request.setRelatedEntityId(9L); request.setRelatedEntityType("ORDER"); request.setData("{}");
        request.setDeduplicationKey("key");
        Class<?> type = Notification.class.getMethod("get" + field).getReturnType();
        SendNotificationRequest.class.getMethod("set" + field, type).invoke(request,
                type == Long.class ? 99L : "changed");
        assertEquals("Deduplication key is already bound to a different notification payload",
                assertThrows(NotificationConflictException.class, () -> service.sendNotification(request)).getMessage());
        verify(repository, never()).findByIdForUpdate(any());
        verifyNoInteractions(firebase);
    }

    private NotificationServiceImpl mappingService() {
        var service = spy(new NotificationServiceImpl(mock(NotificationRepository.class),
                new NotificationMapper(), mock(FirebaseService.class)));
        doReturn(null).when(service).sendNotification(any());
        return service;
    }

    private SendNotificationRequest captured(NotificationServiceImpl service) {
        var captor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(service).sendNotification(captor.capture());
        return captor.getValue();
    }

    private void assertCommon(SendNotificationRequest request, Long userId, Long principalId,
            String title, String message, String type, String priority, Long entityId, String entityType,
            String key) {
        assertEquals(userId, request.getUserId());
        assertEquals(principalId, request.getUserPrincipalId());
        assertEquals(title, request.getTitle());
        assertEquals(message, request.getMessage());
        assertEquals(type, request.getType());
        assertEquals(priority, request.getPriority());
        assertEquals(entityId, request.getRelatedEntityId());
        assertEquals(entityType, request.getRelatedEntityType());
        assertEquals(key, request.getDeduplicationKey());
        assertTrue(request.getSendPush());
    }
}
