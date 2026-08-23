package com.delivery.routing_service.controller;

import com.delivery.routing_service.api.MatrixRequest;
import com.delivery.routing_service.api.MatrixResponse;
import com.delivery.routing_service.api.RouteRequest;
import com.delivery.routing_service.api.RouteResponse;
import com.delivery.routing_service.config.RoutingProperties;
import com.delivery.routing_service.service.RoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/routing/v1")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingService routingService;
    private final RoutingProperties properties;

    @PostMapping("/matrix")
    public ResponseEntity<MatrixResponse> matrix(
            @RequestHeader(value = "Internal-Token", required = false) String token,
            @RequestBody MatrixRequest request) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(routingService.matrix(request));
    }

    @PostMapping("/route")
    public ResponseEntity<RouteResponse> route(
            @RequestHeader(value = "Internal-Token", required = false) String token,
            @RequestBody RouteRequest request) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(routingService.route(request));
    }

    private boolean authorized(String token) {
        return properties.getInternalSecret() != null
                && !properties.getInternalSecret().isBlank()
                && properties.getInternalSecret().equals(token);
    }
}
