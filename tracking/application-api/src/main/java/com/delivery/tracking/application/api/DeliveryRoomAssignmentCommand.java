package com.delivery.tracking.application.api;
public record DeliveryRoomAssignmentCommand(long shipperId, long deliveryId, long orderId,
        long timestamp, String eventId, String status, boolean batch) {}
