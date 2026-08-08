package com.delivery.shipper_service.controller;

import com.delivery.shipper_service.common.constants.ApiPathConstants;
import com.delivery.shipper_service.common.constants.RoleConstants;
import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.payload.BaseResponse;
import com.delivery.shipper_service.payload.PageResponse;
import com.delivery.shipper_service.service.ShipperService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.Valid;

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
            @Valid @RequestBody CreateShipperRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireShipper(actor);
        ShipperResponse response = shipperService.createShipper(request, actor.getUserId(), RoleConstants.SHIPPER);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping("/my-profile")
    public ResponseEntity<BaseResponse<ShipperResponse>> getMyProfile(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireShipper(actor);
        ShipperResponse response = shipperService.getShipperByUserId(actor.getUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PutMapping
    public ResponseEntity<BaseResponse<ShipperResponse>> update(
            @Valid @RequestBody UpdateShipperRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireShipper(actor);
        ShipperResponse response = shipperService.updateShipperByUserId(actor.getUserId(), request);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PatchMapping("/online-status")
    public ResponseEntity<BaseResponse<ShipperResponse>> updateOnlineStatus(
            @RequestParam Boolean isOnline,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireShipper(actor);
        ShipperResponse response = shipperService.updateOnlineStatusByUserId(actor.getUserId(), isOnline);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    // Admin endpoints
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<ShipperResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdmin(actor);
        ShipperResponse response = shipperService.getShipperById(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<ShipperResponse>>> getAll(
            Pageable pageable,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdmin(actor);
        Page<ShipperResponse> response = shipperService.getAllShippers(pageable);
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(response)));
    }

    @GetMapping("/online")
    public ResponseEntity<BaseResponse<List<ShipperResponse>>> getOnlineShippers(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdmin(actor);
        List<ShipperResponse> response = shipperService.getOnlineShippers();
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    private void requireShipper(AuthenticatedActor actor) {
        if (actor == null || !actor.isShipper()) {
            throw new AccessDeniedException("Không có quyền truy cập");
        }
    }

    private void requireAdmin(AuthenticatedActor actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new AccessDeniedException("Không có quyền truy cập");
        }
    }
}
