package com.delivery.tracking.application.api;
import java.util.Set;
public interface DeliveryRoomAssignmentPort {
    void busy(long shipperId,long deliveryId,long timestamp,String eventId);
    void busyBatch(long shipperId,long deliveryId,long timestamp,String eventId);
    void available(long shipperId,long deliveryId,long timestamp);
    void availableBatch(long shipperId,long deliveryId,long timestamp);
    Set<Long> activeDeliveries(long shipperId);
}
