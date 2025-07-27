package com.delivery.shipper_service.controller;

import com.delivery.shipper_service.common.constants.ApiPathConstants;
import com.delivery.shipper_service.common.constants.HttpHeaderConstants;
import com.delivery.shipper_service.dto.request.UpdateLocationRequest;
import com.delivery.shipper_service.dto.response.ShipperLocationResponse;
import com.delivery.shipper_service.payload.BaseResponse;
import com.delivery.shipper_service.service.ShipperLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.SHIPPER_LOCATIONS)
public class ShipperLocationController {

    @Autowired
    private ShipperLocationService shipperLocationService;

    @PutMapping
    public ResponseEntity<BaseResponse<ShipperLocationResponse>> updateLocation(
            @RequestBody UpdateLocationRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long shipperId) {
        ShipperLocationResponse response = shipperLocationService.updateLocation(shipperId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<ShipperLocationResponse>> getMyLocation(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long shipperId) {
        ShipperLocationResponse response = shipperLocationService.getLocationByShipperId(shipperId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping("/nearby")
    public ResponseEntity<BaseResponse<List<ShipperLocationResponse>>> findShippersNearby(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "5.0") Double radiusKm) {
        List<ShipperLocationResponse> response = shipperLocationService.findShippersNearby(lat, lng, radiusKm);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @DeleteMapping
    public ResponseEntity<BaseResponse<Void>> deleteLocation(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long shipperId) {
        shipperLocationService.deleteLocation(shipperId);
        return ResponseEntity.ok(new BaseResponse<>(1, null));
    }
}
