package com.delivery.tracking_service.controller;

import com.delivery.tracking_service.common.constants.ApiPathConstants;
import com.delivery.tracking_service.dto.request.UpdateLocationRequest;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.payload.BaseResponse;
import com.delivery.tracking_service.service.ShipperIdentityResolver;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.tracking.application.api.TrackingPort;
import com.delivery.tracking.application.api.ShipperAvailabilityUseCase;
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
    private final ShipperAvailabilityUseCase availability;
    private final ShipperIdentityResolver shipperIdentityResolver;

    @Autowired
    public ShipperLocationController(TrackingPort tracking, ShipperAvailabilityUseCase availability, ShipperIdentityResolver shipperIdentityResolver) {
        this.tracking = tracking;
        this.availability = availability;
        this.shipperIdentityResolver = shipperIdentityResolver;
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

        availability.markOfflineAndBroadcast(shipperIdentityResolver.requireShipperId(actor.getPrincipalId(), actor.getLegacyUserId()));
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

}
