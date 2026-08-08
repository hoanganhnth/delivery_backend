package com.delivery.tracking_service.controller;

import com.delivery.tracking_service.common.constants.ApiPathConstants;
import com.delivery.tracking_service.dto.request.UpdateLocationRequest;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.payload.BaseResponse;
import com.delivery.tracking_service.service.ShipperLocationService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPathConstants.SHIPPER_LOCATIONS)
@RequiredArgsConstructor
public class ShipperLocationController {

    private final ShipperLocationService shipperLocationService;

    @PostMapping("/update")
    public ResponseEntity<BaseResponse<ShipperLocationResponse>> updateLocation(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @Valid @RequestBody UpdateLocationRequest request) {

        if (actor == null || !actor.isShipper()) {
            return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Không có quyền truy cập"));
        }

        ShipperLocationResponse response = shipperLocationService.updateLocation(actor.getUserId(), request);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật vị trí thành công"));
    }

    @PostMapping("/offline")
    public ResponseEntity<BaseResponse<String>> markOffline(
            @AuthenticationPrincipal AuthenticatedActor actor) {

        if (actor == null || !actor.isShipper()) {
            return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Không có quyền truy cập"));
        }

        shipperLocationService.markShipperOffline(actor.getUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, "Đã đánh dấu offline thành công"));
    }
}
