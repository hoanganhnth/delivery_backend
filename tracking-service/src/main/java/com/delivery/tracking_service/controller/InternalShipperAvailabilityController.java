package com.delivery.tracking_service.controller;

import com.delivery.tracking_service.payload.BaseResponse;
import com.delivery.tracking_service.service.ShipperLocationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal command boundary for an explicit shipper offline transition.
 * Tracking owns the transient availability projection consumed by Match.
 */
@RestController
@RequestMapping("/api/tracking/internal/shippers")
public class InternalShipperAvailabilityController {

    private final ShipperLocationService shipperLocationService;
    private final String internalSecret;

    public InternalShipperAvailabilityController(
            ShipperLocationService shipperLocationService,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.shipperLocationService = shipperLocationService;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/{shipperId}/offline")
    public ResponseEntity<BaseResponse<Void>> markOffline(
            @PathVariable Long shipperId,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (!isInternalRequest(internalToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Forbidden"));
        }
        if (shipperId == null || shipperId <= 0) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "shipperId must be positive"));
        }

        shipperLocationService.markShipperOffline(shipperId);
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Shipper marked offline"));
    }

    private boolean isInternalRequest(String internalToken) {
        return internalSecret != null && !internalSecret.isBlank()
                && internalSecret.equals(internalToken);
    }
}
