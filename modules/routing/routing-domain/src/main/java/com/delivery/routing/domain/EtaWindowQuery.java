package com.delivery.routing.domain;

public record EtaWindowQuery(Coordinate origin, Coordinate destination, int prepMinutes) {
    public EtaWindowQuery {
        if (origin == null || destination == null || prepMinutes < 1 || prepMinutes > 240) {
            throw new IllegalArgumentException("ETA requires coordinates and prepMinutes between 1 and 240");
        }
    }
}
