package com.delivery.tracking.application.api;
public interface DeliveryRoomIndexPort {
    void activate(long deliveryId,long shipperId);
    void end(long deliveryId,long shipperId);
}
