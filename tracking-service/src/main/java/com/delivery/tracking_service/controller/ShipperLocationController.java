package com.delivery.tracking_service.controller;

import com.delivery.tracking_service.common.constants.ApiPathConstants;
import com.delivery.tracking_service.common.constants.HttpHeaderConstants;
import com.delivery.tracking_service.common.constants.RoleConstants;
import com.delivery.tracking_service.dto.request.UpdateLocationRequest;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.payload.BaseResponse;
import com.delivery.tracking_service.service.ShipperLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPathConstants.SHIPPER_LOCATIONS)
@RequiredArgsConstructor
public class ShipperLocationController {

    private final ShipperLocationService shipperLocationService;

    @PostMapping("/update")
    public ResponseEntity<BaseResponse<ShipperLocationResponse>> updateLocation(
            @RequestHeader(HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(HttpHeaderConstants.X_ROLE) String role,
            @Valid @RequestBody UpdateLocationRequest request) {
        
        // Only shippers can update their own location
        if (!RoleConstants.SHIPPER.equals(role)) {
            return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Không có quyền truy cập"));
        }
        
        ShipperLocationResponse response = shipperLocationService.updateLocation(userId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật vị trí thành công"));
    }

    @PostMapping("/offline")
    public ResponseEntity<BaseResponse<String>> markOffline(
            @RequestHeader(HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(HttpHeaderConstants.X_ROLE) String role) {
        
        // Only shippers can mark themselves offline
        if (!RoleConstants.SHIPPER.equals(role)) {
            return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Không có quyền truy cập"));
        }
        
        shipperLocationService.markShipperOffline(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, "Đã đánh dấu offline thành công"));
    }

}
