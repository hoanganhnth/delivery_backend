package com.delivery.tracking.application.api;
public interface DeliveryRoomIndexPort {
    void activate(long deliveryId,long shipperId);
    void synchronize(long shipperId, java.util.Set<Long> deliveryIds);
    void subscribe(long deliveryId,long shipperId,String sessionId);
    void end(long deliveryId,long shipperId);
}
