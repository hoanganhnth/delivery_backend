package com.delivery.routing.application;

import com.delivery.routing.application.api.RoutingPort;
import com.delivery.routing.application.api.RoutingProviderPort;
import com.delivery.routing.domain.Coordinate;
import com.delivery.routing.domain.EtaWindow;
import com.delivery.routing.domain.EtaWindowQuery;
import com.delivery.routing.domain.MatrixQuery;
import com.delivery.routing.domain.MatrixResult;
import com.delivery.routing.domain.RouteQuery;
import com.delivery.routing.domain.RouteResult;
import java.util.List;
import java.util.Objects;

/** Framework-free routing policy and use cases. */
public final class DefaultRoutingService implements RoutingPort {
    private static final int ETA_UPPER_BOUND_MINUTES = 10;

    private final RoutingProviderPort provider;
    private final int fallbackSpeedKmh;

    public DefaultRoutingService(RoutingProviderPort provider, int fallbackSpeedKmh) {
        this.provider = Objects.requireNonNull(provider, "provider");
        if (fallbackSpeedKmh < 1) {
            throw new IllegalArgumentException("fallbackSpeedKmh must be positive");
        }
        this.fallbackSpeedKmh = fallbackSpeedKmh;
    }

    @Override
    public RouteResult route(RouteQuery query) {
        Objects.requireNonNull(query, "query");
        try {
            RouteResult result = provider.route(query);
            if (result != null) {
                return result;
            }
        } catch (RuntimeException ignored) {
            // Provider failure is an expected condition; use the deterministic fallback.
        }
        return fallbackRoute(query);
    }

    @Override
    public List<MatrixResult> matrix(MatrixQuery query) {
        Objects.requireNonNull(query, "query");
        try {
            List<MatrixResult> results = provider.matrix(query);
            if (results != null && results.size() == query.destinations().size()
                    && results.stream().allMatch(Objects::nonNull)) {
                return List.copyOf(results);
            }
        } catch (RuntimeException ignored) {
            // Provider failure is an expected condition; use the deterministic fallback.
        }
        return query.destinations().stream()
                .map(destination -> fallbackResult(query.origin(), destination.id(), destination.coordinate()))
                .toList();
    }

    @Override
    public EtaWindow etaWindow(EtaWindowQuery query) {
        Objects.requireNonNull(query, "query");
        RouteResult route = route(new RouteQuery("driving", query.origin(), query.destination(), null, false));
        int drivingMinutes = Math.max(1, (int) Math.ceil(route.durationSeconds() / 60d));
        int minimum = Math.addExact(drivingMinutes, query.prepMinutes());
        return new EtaWindow(minimum, Math.addExact(minimum, ETA_UPPER_BOUND_MINUTES), route.source());
    }

    private RouteResult fallbackRoute(RouteQuery query) {
        MatrixResult result = fallbackResult(query.origin(), "route", query.destination());
        return new RouteResult(result.durationSeconds(), result.distanceMeters(), null, "GEODESIC_FALLBACK");
    }

    private MatrixResult fallbackResult(Coordinate origin, String id, Coordinate destination) {
        long meters = Math.round(haversineMeters(origin, destination));
        double metersPerSecond = fallbackSpeedKmh * 1000d / 3600d;
        return new MatrixResult(id, Math.max(1, Math.round(meters / metersPerSecond)), meters,
                "GEODESIC_FALLBACK");
    }

    private double haversineMeters(Coordinate a, Coordinate b) {
        double earthRadius = 6_371_000d;
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
