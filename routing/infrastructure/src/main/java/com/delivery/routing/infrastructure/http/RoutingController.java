package com.delivery.routing.infrastructure.http;

import com.delivery.routing.contracts.EtaWindowRequest;
import com.delivery.routing.contracts.EtaWindowResponse;
import com.delivery.routing.contracts.MatrixRequest;
import com.delivery.routing.contracts.MatrixResponse;
import com.delivery.routing.contracts.RouteRequest;
import com.delivery.routing.contracts.RouteResponse;
import com.delivery.routing.application.api.RoutingPort;
import com.delivery.routing.domain.Coordinate;
import com.delivery.routing.domain.EtaWindowQuery;
import com.delivery.routing.domain.MatrixQuery;
import com.delivery.routing.domain.MatrixResult;
import com.delivery.routing.domain.RouteQuery;
import com.delivery.routing.infrastructure.config.RoutingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/internal/routing/v1")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingPort routing;
    private final RoutingProperties properties;

    @PostMapping("/matrix")
    public ResponseEntity<MatrixResponse> matrix(
            @RequestHeader(value = "Internal-Token", required = false) String token,
            @RequestBody MatrixRequest request) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        validateMatrix(request);
        MatrixQuery query = new MatrixQuery(request.profile(), coordinate(request.origin()),
                request.destinations().stream()
                        .map(destination -> new MatrixQuery.Destination(destination.id(),
                                coordinate(destination.coordinate())))
                        .toList(), request.departureAt());
        return ResponseEntity.ok(new MatrixResponse(routing.matrix(query).stream()
                .map(this::toResponse)
                .toList(), Instant.now()));
    }

    @PostMapping("/route")
    public ResponseEntity<RouteResponse> route(
            @RequestHeader(value = "Internal-Token", required = false) String token,
            @RequestBody RouteRequest request) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        validateRoute(request);
        RouteQuery query = new RouteQuery(request.profile(), coordinate(request.origin()),
                coordinate(request.destination()), request.departureAt(), request.includeGeometry());
        var result = routing.route(query);
        return ResponseEntity.ok(new RouteResponse(result.durationSeconds(), result.distanceMeters(),
                result.geometry(), result.source()));
    }

    @PostMapping("/eta-window")
    public ResponseEntity<EtaWindowResponse> etaWindow(
            @RequestHeader(value = "Internal-Token", required = false) String token,
            @RequestBody EtaWindowRequest request) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        validateEtaWindow(request);
        var result = routing.etaWindow(new EtaWindowQuery(coordinate(request.origin()),
                coordinate(request.destination()), request.prepMinutes()));
        return ResponseEntity.ok(new EtaWindowResponse(result.minMinutes(), result.maxMinutes(), result.source()));
    }

    private boolean authorized(String token) {
        return properties.getInternalSecret() != null
                && !properties.getInternalSecret().isBlank()
                && properties.getInternalSecret().equals(token);
    }

    private void validateMatrix(MatrixRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.origin() == null || request.destinations() == null || request.destinations().isEmpty()
                || request.destinations().size() > 25
                || request.destinations().stream().anyMatch(destination -> destination == null
                || destination.coordinate() == null)) {
            throw new IllegalArgumentException("Matrix requires one origin and 1-25 destinations");
        }
    }

    private void validateRoute(RouteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.origin() == null || request.destination() == null) {
            throw new IllegalArgumentException("Route origin and destination are required");
        }
    }

    private void validateEtaWindow(EtaWindowRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.origin() == null || request.destination() == null || request.prepMinutes() == null
                || request.prepMinutes() < 1 || request.prepMinutes() > 240) {
            throw new IllegalArgumentException("ETA requires coordinates and prepMinutes between 1 and 240");
        }
    }

    private Coordinate coordinate(com.delivery.routing.contracts.Coordinate coordinate) {
        return new Coordinate(coordinate.lat(), coordinate.lng());
    }

    private MatrixResponse.Result toResponse(MatrixResult result) {
        return new MatrixResponse.Result(result.id(), result.durationSeconds(), result.distanceMeters(),
                result.source());
    }
}
