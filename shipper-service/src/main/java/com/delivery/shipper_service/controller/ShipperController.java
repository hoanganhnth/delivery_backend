package com.delivery.shipper_service.controller;

import com.delivery.shipper_service.common.constants.ApiPathConstants;
import com.delivery.shipper_service.common.constants.HttpHeaderConstants;
import com.delivery.shipper_service.common.constants.RoleConstants;
import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.payload.BaseResponse;
import com.delivery.shipper_service.payload.PageResponse;
import com.delivery.shipper_service.service.ShipperService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.Valid;

@RestController
@RequestMapping(ApiPathConstants.SHIPPERS)
public class ShipperController {

    private final ShipperService shipperService;

    public ShipperController(ShipperService shipperService) {
        this.shipperService = shipperService;
    }

    @PostMapping
    public ResponseEntity<BaseResponse<ShipperResponse>> create(
            @Valid @RequestBody CreateShipperRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        requireRole(role, RoleConstants.SHIPPER);
        ShipperResponse response = shipperService.createShipper(request, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping("/my-profile")
    public ResponseEntity<BaseResponse<ShipperResponse>> getMyProfile(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE) String role) {
        requireRole(role, RoleConstants.SHIPPER);
        ShipperResponse response = shipperService.getShipperByUserId(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PutMapping
    public ResponseEntity<BaseResponse<ShipperResponse>> update(
            @Valid @RequestBody UpdateShipperRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE) String role) {
        requireRole(role, RoleConstants.SHIPPER);
        ShipperResponse response = shipperService.updateShipperByUserId(userId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PatchMapping("/online-status")
    public ResponseEntity<BaseResponse<ShipperResponse>> updateOnlineStatus(
            @RequestParam Boolean isOnline,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE) String role) {
        requireRole(role, RoleConstants.SHIPPER);
        ShipperResponse response = shipperService.updateOnlineStatusByUserId(userId, isOnline);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    // Admin endpoints
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<ShipperResponse>> getById(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE) String role) {
        requireRole(role, RoleConstants.ADMIN);
        ShipperResponse response = shipperService.getShipperById(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<ShipperResponse>>> getAll(
            Pageable pageable,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE) String role) {
        requireRole(role, RoleConstants.ADMIN);
        Page<ShipperResponse> response = shipperService.getAllShippers(pageable);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(response)));
    }

    @GetMapping("/online")
    public ResponseEntity<BaseResponse<List<ShipperResponse>>> getOnlineShippers(
            @RequestHeader(value = HttpHeaderConstants.X_ROLE) String role) {
        requireRole(role, RoleConstants.ADMIN);
        List<ShipperResponse> response = shipperService.getOnlineShippers();
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    private void requireRole(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new AccessDeniedException("Không có quyền truy cập");
        }
    }
}
