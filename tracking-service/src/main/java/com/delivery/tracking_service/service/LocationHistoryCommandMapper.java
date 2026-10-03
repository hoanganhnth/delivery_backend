package com.delivery.tracking_service.service;
import com.delivery.tracking.application.api.LocationHistoryCommand;
import com.delivery.tracking_service.dto.event.ShipperLocationUpdatedEvent;
/** Transport facts only; raw payload is retained byte-for-byte for the core replay fence. */
public final class LocationHistoryCommandMapper {
    private LocationHistoryCommandMapper() {}
    public static LocationHistoryCommand from(ShipperLocationUpdatedEvent event, String raw) {
        return new LocationHistoryCommand(event.getShipperId(), event.getLatitude(), event.getLongitude(), event.getIsOnline(),
                event.getTimestamp(), event.getEventId(), event.getDeliveryId(), event.getAccuracy(), event.getSpeed(),
                event.getHeading(), event.getSource(), raw);
    }
}
