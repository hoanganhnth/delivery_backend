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
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long creatorId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        ShipperResponse response = shipperService.createShipper(request, creatorId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<ShipperResponse>> update(
            @PathVariable Long id,
            @RequestBody UpdateShipperRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long creatorId) {
        ShipperResponse response = shipperService.updateShipper(id, request, creatorId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long creatorId) {
        shipperService.deleteShipper(id, creatorId);
        return ResponseEntity.ok(new BaseResponse<>(1, null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<ShipperResponse>> getById(@PathVariable Long id) {
        ShipperResponse response = shipperService.getShipperById(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<BaseResponse<ShipperResponse>> getByUserId(@PathVariable Long userId) {
        ShipperResponse response = shipperService.getShipperByUserId(userId);
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

    @PatchMapping("/{id}/online-status")
    public ResponseEntity<BaseResponse<ShipperResponse>> updateOnlineStatus(
            @PathVariable Long id,
            @RequestParam Boolean isOnline,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long creatorId) {
        ShipperResponse response = shipperService.updateOnlineStatus(id, isOnline, creatorId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }
}
