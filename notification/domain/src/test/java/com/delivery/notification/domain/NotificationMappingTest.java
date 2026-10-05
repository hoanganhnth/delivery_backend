package com.delivery.notification.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationMappingTest {
    private static final UUID EVENT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void orderPreservesRecipientPrincipalTextAndEventIdentity() {
        var intent = NotificationMapping.orderCreated(EVENT, 7L, 71L, 9L, " Nhà hàng ");
        assertEquals(7L, intent.userId());
        assertEquals(71L, intent.userPrincipalId());
        assertEquals("Đơn hàng đã được tạo", intent.title());
        assertEquals("Đơn hàng #9 từ  Nhà hàng  đã được tạo thành công", intent.message());
        assertEquals("ORDER_CREATED", intent.type());
        assertEquals("MEDIUM", intent.priority());
        assertEquals(9L, intent.relatedEntityId());
        assertEquals("ORDER", intent.relatedEntityType());
        assertEquals("order-created:" + EVENT, intent.deduplicationKey());
        assertTrue(intent.sendPush());
        assertNull(intent.data());
        assertNull(NotificationMapping.orderCreated(EVENT, 7L, null, 9L, "R").userPrincipalId());
        assertNotEquals(intent.deduplicationKey(), NotificationMapping.orderCreated(UUID.randomUUID(), 7L, 71L, 9L, "R").deduplicationKey());
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
    void allDeliveryStatusesHaveExactGenericMapping(String status, String title, String message) {
        for (String absent : new String[]{null, "", "  "}) {
            var intent = NotificationMapping.deliveryStatus(EVENT, 7L, 71L, 11L, status, absent);
            assertEquals(title, intent.title());
            assertEquals(message, intent.message());
            assertEquals("DELIVERY_" + status, intent.type());
            assertEquals(7L, intent.userId());
            assertEquals(71L, intent.userPrincipalId());
            assertEquals(11L, intent.relatedEntityId());
            assertEquals("DELIVERY", intent.relatedEntityType());
            assertEquals("HIGH", intent.priority());
            assertEquals("delivery-status:" + EVENT, intent.deduplicationKey());
            assertTrue(intent.sendPush());
            assertNull(intent.data());
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "ASSIGNED| A  đã được phân công giao đơn hàng của bạn",
        "PICKED_UP| A  đã lấy đơn hàng và chuẩn bị giao",
        "DELIVERING| A  đang trên đường giao hàng",
        "DELIVERED|Đơn hàng đã được  A  giao thành công"
    }, ignoreLeadingAndTrailingWhitespace = false)
    void namedDeliveryMessagesPreserveNameWithoutTrimming(String status, String message) {
        assertEquals(message, NotificationMapping.deliveryStatus(EVENT, 7L, null, 11L, status, " A ").message());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETURNING", "RETURNED", "UNKNOWN", "delivering", "", " "})
    void unsupportedStatusesRemainIllegalArguments(String status) {
        var error = assertThrows(IllegalArgumentException.class,
                () -> NotificationMapping.deliveryStatus(EVENT, 7L, null, 11L, status, null));
        assertEquals("Unknown delivery status: " + status, error.getMessage());
    }

    @Test
    void nullStatusRetainsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> NotificationMapping.deliveryStatus(EVENT, 7L, null, 11L, null, null));
    }

    @Test
    void offerSelectsShipperAndPreservesLocaleSensitiveTextDataAndKeys() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            var intent = NotificationMapping.shipperOffer(5L, 9L, "R", "P", "D", 1.25, "offer-1");
            assertEquals(5L, intent.userId());
            assertNull(intent.userPrincipalId());
            assertEquals("🎯 Đơn hàng phù hợp!", intent.title());
            assertEquals("Đơn hàng #9 từ R - cách điểm lấy khoảng 1.3km. Mở ứng dụng để xem offer hiện tại.", intent.message());
            assertEquals("MATCH_FOUND", intent.type());
            assertEquals("HIGH", intent.priority());
            assertEquals(9L, intent.relatedEntityId());
            assertEquals("ORDER", intent.relatedEntityType());
            assertEquals("shipper-offer:offer-1:5", intent.deduplicationKey());
            assertTrue(intent.sendPush());
            assertEquals(java.util.Map.of("pickupAddress", "P", "deliveryAddress", "D", "distance", 1.25,
                    "orderId", 9L, "recoveryEndpoint", "/api/deliveries/offers/current"), intent.data());
            assertThrows(UnsupportedOperationException.class, () -> intent.data().put("extra", "x"));
            assertNotEquals(intent.deduplicationKey(), NotificationMapping.shipperOffer(6L, 9L, "R", "P", "D", 1.25, "offer-1").deduplicationKey());
            assertNotEquals(intent.deduplicationKey(), NotificationMapping.shipperOffer(5L, 9L, "R", "P", "D", 1.25, "offer-2").deduplicationKey());
            Locale.setDefault(Locale.GERMANY);
            assertTrue(NotificationMapping.shipperOffer(5L, 9L, "R", "P", "D", 1.25, "offer-1").message().contains("1,3km"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void directOfferCallsRetainPermissiveContractUnlikeListenerValidation() {
        var intent = NotificationMapping.shipperOffer(5L, 9L, null, null, null, null, null);
        assertEquals("shipper-offer:null:5", intent.deduplicationKey());
        assertNull(intent.data().get("distance"));
        assertNull(intent.data().get("pickupAddress"));
        assertTrue(intent.message().contains("null"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankRestaurantIsRejected(String name) {
        assertEquals("canonical restaurantName is required", assertThrows(IllegalArgumentException.class,
                () -> NotificationMapping.orderCreated(EVENT, 7L, null, 9L, name)).getMessage());
    }

    @Test
    void missingEventIdIsRejectedFirst() {
        assertEquals("stable eventId is required", assertThrows(IllegalArgumentException.class,
                () -> NotificationMapping.orderCreated(null, null, null, null, null)).getMessage());
        assertEquals("stable eventId is required", assertThrows(IllegalArgumentException.class,
                () -> NotificationMapping.deliveryStatus(null, null, null, null, null, null)).getMessage());
    }

    @Test
    void allMappingIdentitiesRejectNullZeroAndNegativeInOriginalOrder() {
        for (Long invalid : new Long[]{null, 0L, -1L}) {
            assertInvalid("userId", () -> NotificationMapping.orderCreated(EVENT, invalid, null, 9L, "R"));
            assertInvalid("orderId", () -> NotificationMapping.orderCreated(EVENT, 7L, null, invalid, "R"));
            assertInvalid("userId", () -> NotificationMapping.deliveryStatus(EVENT, invalid, null, 11L, "PENDING", null));
            assertInvalid("deliveryId", () -> NotificationMapping.deliveryStatus(EVENT, 7L, null, invalid, "PENDING", null));
            assertInvalid("shipperId", () -> NotificationMapping.shipperOffer(invalid, 9L, "R", "P", "D", 1.0, "offer"));
            assertInvalid("orderId", () -> NotificationMapping.shipperOffer(5L, invalid, "R", "P", "D", 1.0, "offer"));
        }
    }

    private void assertInvalid(String field, org.junit.jupiter.api.function.Executable action) {
        assertEquals(field + " must be positive", assertThrows(IllegalArgumentException.class, action).getMessage());
    }
}
