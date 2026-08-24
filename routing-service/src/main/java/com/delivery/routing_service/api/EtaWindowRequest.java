package com.delivery.routing_service.api;

/** Internal request for a customer-facing, bounded ETA window. */
public record EtaWindowRequest(
        Coordinate origin,
        Coordinate destination,
        Integer prepMinutes) {
}
