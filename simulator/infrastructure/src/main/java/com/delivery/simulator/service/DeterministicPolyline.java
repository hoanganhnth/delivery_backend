package com.delivery.simulator.service;
/** Compatibility view for the domain route. */
public final class DeterministicPolyline {
    public record Position(double latitude, double longitude, double headingDegrees) { }
    private final com.delivery.simulator.domain.DeterministicPolyline route;
    public DeterministicPolyline(double a, double b, double c, double d, long seed) {
        route = new com.delivery.simulator.domain.DeterministicPolyline(a,b,c,d,seed);
    }
    public Position positionAfterSeconds(long seconds, double speed) {
        var value = route.positionAfterSeconds(seconds,speed);
        return new Position(value.latitude(),value.longitude(),value.headingDegrees());
    }
}
