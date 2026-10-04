package com.delivery.tracking.application;
import com.delivery.tracking.application.api.*;
import java.util.Objects;
import java.util.Set;
/** Shared projection supplies all batch rooms; authorized scope is the rolling fallback. */
public final class DefaultDeliveryRoomSubscriptionUseCase implements DeliveryRoomSubscriptionUseCase {
    private final DeliveryRoomAssignmentPort assignments;
    private final DeliveryRoomIndexPort rooms;
    public DefaultDeliveryRoomSubscriptionUseCase(DeliveryRoomAssignmentPort assignments,DeliveryRoomIndexPort rooms) {
        this.assignments=Objects.requireNonNull(assignments,"assignments");
        this.rooms=Objects.requireNonNull(rooms,"rooms");
    }
    @Override public void subscribeAuthorized(long deliveryId,long shipperId,String sessionId) {
        if(deliveryId<=0 || shipperId<=0 || sessionId==null || sessionId.isBlank())
            throw new IllegalArgumentException("positive delivery/shipper identity and sessionId are required");
        rooms.withinUpdate(shipperId,()->{
            Set<Long> active=assignments.activeDeliveries(shipperId);
            rooms.synchronize(shipperId,active.contains(deliveryId)?active:Set.of(deliveryId));
            rooms.subscribe(deliveryId,shipperId,sessionId);
        });
    }
}
