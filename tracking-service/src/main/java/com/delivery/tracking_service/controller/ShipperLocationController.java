package com.delivery.tracking_service.controller;

import com.delivery.tracking_service.common.constants.ApiPathConstants;
import com.delivery.tracking_service.dto.request.UpdateLocationRequest;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.payload.BaseResponse;
import com.delivery.tracking_service.service.ShipperLocationService;
import com.delivery.tracking_service.service.ShipperIdentityResolver;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.tracking.application.api.TrackingPort;
import com.delivery.tracking.application.api.UpdateLocationCommand;
import com.delivery.tracking.domain.Coordinate;
import com.delivery.tracking.domain.LocationSnapshot;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPathConstants.SHIPPER_LOCATIONS)
public class ShipperLocationController {

    private final TrackingPort tracking;
    private final ShipperIdentityResolver shipperIdentityResolver;

    @Autowired
    public ShipperLocationController(TrackingPort tracking, ShipperIdentityResolver shipperIdentityResolver) {
        this.tracking = tracking;
        this.shipperIdentityResolver = shipperIdentityResolver;
    }

    /** Compatibility constructor for adapters instantiated directly by older tests. */
    public ShipperLocationController(ShipperLocationService legacy, ShipperIdentityResolver identities) {
        this(new LegacyTrackingPort(legacy), identities);
    }

    @PostMapping("/update")
    public ResponseEntity<BaseResponse<ShipperLocationResponse>> updateLocation(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @Valid @RequestBody UpdateLocationRequest request) {

        if (actor == null || !actor.isShipper()) {
            return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Không có quyền truy cập"));
        }

        long shipperId = shipperIdentityResolver.requireShipperId(actor.getPrincipalId(), actor.getLegacyUserId());
        LocationSnapshot snapshot = tracking.updateLocation(new UpdateLocationCommand(shipperId,
                new Coordinate(request.getLatitude(), request.getLongitude()), request.getAccuracy(),
                request.getSpeed(), request.getHeading(), Boolean.TRUE.equals(request.getIsOnline())));
        ShipperLocationResponse response = toResponse(snapshot);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật vị trí thành công"));
    }

    @PostMapping("/offline")
    public ResponseEntity<BaseResponse<String>> markOffline(
            @AuthenticationPrincipal AuthenticatedActor actor) {

        if (actor == null || !actor.isShipper()) {
            return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Không có quyền truy cập"));
        }

        tracking.markOffline(shipperIdentityResolver.requireShipperId(actor.getPrincipalId(), actor.getLegacyUserId()));
        return ResponseEntity.ok(new BaseResponse<>(1, "Đã đánh dấu offline thành công"));
    }

    private ShipperLocationResponse toResponse(LocationSnapshot snapshot) {
        var response = new ShipperLocationResponse();
        response.setShipperId(snapshot.shipperId());
        response.setLatitude(snapshot.coordinate().latitude());
        response.setLongitude(snapshot.coordinate().longitude());
        response.setAccuracy(snapshot.accuracy());
        response.setSpeed(snapshot.speed());
        response.setHeading(snapshot.heading());
        response.setIsOnline(snapshot.online());
        response.setLastPing(snapshot.lastPing().toString());
        response.setUpdatedAt(snapshot.updatedAt().toString());
        return response;
    }

    private static final class LegacyTrackingPort implements TrackingPort {
        private final ShipperLocationService legacy;
        private LegacyTrackingPort(ShipperLocationService legacy) { this.legacy = legacy; }
        @Override public LocationSnapshot updateLocation(UpdateLocationCommand command) {
            var request = new UpdateLocationRequest();
            request.setLatitude(command.coordinate().latitude()); request.setLongitude(command.coordinate().longitude());
            request.setAccuracy(command.accuracy()); request.setSpeed(command.speed()); request.setHeading(command.heading());
            request.setIsOnline(command.online());
            var result = legacy.updateLocation(command.shipperId(), request);
            return new LocationSnapshot(command.shipperId(), command.coordinate(), command.accuracy(), command.speed(),
                    command.heading(), command.online(), java.time.Instant.parse(result.getLastPing()),
                    java.time.Instant.parse(result.getUpdatedAt()));
        }
        @Override public LocationSnapshot markOffline(long shipperId) {
            legacy.markShipperOffline(shipperId);
            throw new IllegalStateException("legacy compatibility path does not return a snapshot");
        }
    }
}
