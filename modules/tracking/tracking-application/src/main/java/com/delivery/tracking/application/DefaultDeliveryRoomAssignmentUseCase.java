package com.delivery.tracking.application;
import com.delivery.tracking.application.api.*;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
/** Assignment mutation is atomically fenced by the store; local rooms follow admitted facts. */
public final class DefaultDeliveryRoomAssignmentUseCase implements DeliveryRoomAssignmentUseCase {
    private final DeliveryRoomAssignmentPort assignments;
    private final DeliveryRoomIndexPort rooms;
    public DefaultDeliveryRoomAssignmentUseCase(DeliveryRoomAssignmentPort assignments, DeliveryRoomIndexPort rooms) {
        this.assignments = Objects.requireNonNull(assignments,"assignments");
        this.rooms = Objects.requireNonNull(rooms,"rooms");
    }
    @Override public void apply(DeliveryRoomAssignmentCommand command) {
        Objects.requireNonNull(command,"command");
        positive(command.shipperId(),"shipperId"); positive(command.deliveryId(),"deliveryId");
        positive(command.orderId(),"orderId"); positive(command.timestamp(),"timestamp");
        if(command.eventId()==null || command.eventId().isBlank())throw new IllegalArgumentException("eventId is required");
        UUID.fromString(command.eventId());
        if(command.status()==null || command.status().isBlank())throw new IllegalArgumentException("status is required");
        String status = command.status().toUpperCase(Locale.ROOT);
        if(!Set.of("BUSY","AVAILABLE").contains(status))throw new IllegalArgumentException("Unsupported shipper status");
        if("BUSY".equals(status)) {
            if(command.batch())assignments.busyBatch(command.shipperId(),command.deliveryId(),command.timestamp(),command.eventId());
            else assignments.busy(command.shipperId(),command.deliveryId(),command.timestamp(),command.eventId());
            var active=assignments.activeDeliveries(command.shipperId());
            if(active.contains(command.deliveryId())) {
                if(command.batch())rooms.synchronize(command.shipperId(),active);
                else rooms.activate(command.deliveryId(),command.shipperId());
            }
        } else {
            if(command.batch())assignments.availableBatch(command.shipperId(),command.deliveryId(),command.timestamp());
            else assignments.available(command.shipperId(),command.deliveryId(),command.timestamp());
            var active=assignments.activeDeliveries(command.shipperId());
            if(!active.contains(command.deliveryId())) {
                if(command.batch())rooms.synchronize(command.shipperId(),active);
                else rooms.end(command.deliveryId(),command.shipperId());
            }
        }
    }
    private static void positive(long value,String field) {
        if(value<=0)throw new IllegalArgumentException(field+" must be positive");
    }
}
