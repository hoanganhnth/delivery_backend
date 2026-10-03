package com.delivery.tracking.application.api;
/** Caller must first obtain canonical Delivery participant authorization. */
public interface DeliveryRoomSubscriptionUseCase {
    void subscribeAuthorized(long deliveryId,long shipperId,String sessionId);
}
