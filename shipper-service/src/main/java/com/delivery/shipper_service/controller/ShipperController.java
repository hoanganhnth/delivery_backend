package com.delivery.shipper_service.controller;

import com.delivery.shipper_service.common.constants.ApiPathConstants;
import com.delivery.shipper_service.common.constants.HttpHeaderConstants;
import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.payload.BaseResponse;
import com.delivery.shipper_service.service.ShipperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.SHIPPERS)
public class ShipperController {

    private final ShipperService shipperService;

    public ShipperController(ShipperService shipperService) {
        this.shipperService = shipperService;
    }

    @PostMapping
    public ResponseEntity<BaseResponse<ShipperResponse>> create(
            @RequestBody CreateShipperRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) String userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        ShipperResponse response = shipperService.createShipper(request, Long.parseLong(userId), role);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping("/my-profile")
    public ResponseEntity<BaseResponse<ShipperResponse>> getMyProfile(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) String userId) {
        ShipperResponse response = shipperService.getShipperByUserId(Long.parseLong(userId));
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PutMapping
    public ResponseEntity<BaseResponse<ShipperResponse>> update(
            @RequestBody UpdateShipperRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) String userId) {
        ShipperResponse response = shipperService.updateShipperByUserId(Long.parseLong(userId), request);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @DeleteMapping
    public ResponseEntity<BaseResponse<Void>> delete(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) String userId) {
        shipperService.deleteShipperByUserId(Long.parseLong(userId));
        return ResponseEntity.ok(new BaseResponse<>(1, null));
    }

    @PatchMapping("/online-status")
    public ResponseEntity<BaseResponse<ShipperResponse>> updateOnlineStatus(
            @RequestParam Boolean isOnline,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) String userId) {
        ShipperResponse response = shipperService.updateOnlineStatusByUserId(Long.parseLong(userId), isOnline);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    // Admin endpoints
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<ShipperResponse>> getById(@PathVariable Long id) {
        ShipperResponse response = shipperService.getShipperById(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<ShipperResponse>>> getAll() {
        List<ShipperResponse> response = shipperService.getAllShippers();
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping("/online")
    public ResponseEntity<BaseResponse<List<ShipperResponse>>> getOnlineShippers() {
        List<ShipperResponse> response = shipperService.getOnlineShippers();
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }
}
