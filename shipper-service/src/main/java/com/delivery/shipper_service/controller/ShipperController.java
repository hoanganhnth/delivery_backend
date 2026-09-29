package com.delivery.shipper_service.controller;

import com.delivery.shipper_service.common.constants.ApiPathConstants;
import com.delivery.shipper_service.common.constants.RoleConstants;
import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.mapper.ShipperMapper;
import com.delivery.shipper_service.payload.BaseResponse;
import com.delivery.shipper_service.payload.PageResponse;
import com.delivery.shipper.application.api.ShipperUseCases;
import com.delivery.shipper.application.api.ShipperCommands;
import com.delivery.shipper.domain.identity.ShipperRole;
import com.delivery.shipper.domain.read.PageRequest;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    private final ShipperUseCases.CreateProfile createProfile;
    private final ShipperUseCases.UpdateProfile updateProfile;
    private final ShipperUseCases.ReadSelf readSelf;
    private final ShipperUseCases.ReadById readById;
    private final ShipperUseCases.ReadPage readPage;
    private final ShipperUseCases.SetOnlineStatus setOnlineStatus;
    private final ShipperMapper shipperMapper;

    public ShipperController(ShipperUseCases.CreateProfile createProfile,
                             ShipperUseCases.UpdateProfile updateProfile,
                             ShipperUseCases.ReadSelf readSelf,
                             ShipperUseCases.ReadById readById,
                             ShipperUseCases.ReadPage readPage,
                             ShipperUseCases.SetOnlineStatus setOnlineStatus,
                             ShipperMapper shipperMapper) {
        this.createProfile = createProfile;
        this.updateProfile = updateProfile;
        this.readSelf = readSelf;
        this.readById = readById;
        this.readPage = readPage;
        this.setOnlineStatus = setOnlineStatus;
        this.shipperMapper = shipperMapper;
    }

    private ShipperCommands.Actor toActor(AuthenticatedActor actor) {
        ShipperRole role = actor.isAdmin() ? ShipperRole.ADMIN : ShipperRole.SHIPPER;
        return new ShipperCommands.Actor(actor.getPrincipalId(), actor.getLegacyUserId(), role);
    }

    @PostMapping
    public ResponseEntity<BaseResponse<ShipperResponse>> create(
            @Valid @RequestBody CreateShipperRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireShipper(actor);
        var command = new ShipperCommands.CreateProfile(
                toActor(actor),
                request.getFullName(),
                request.getVehicleType(),
                request.getLicenseNumber(),
                request.getIdCard(),
                request.getPhone(),
                request.getLicensePlate()
        );
        var result = createProfile.execute(command);
        return ResponseEntity.ok(new BaseResponse<>(1, shipperMapper.toResponse(result.profile())));
    }

    @GetMapping("/my-profile")
    public ResponseEntity<BaseResponse<ShipperResponse>> getMyProfile(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireShipper(actor);
        var result = readSelf.execute(toActor(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, shipperMapper.toResponse(result)));
    }

    @PutMapping
    public ResponseEntity<BaseResponse<ShipperResponse>> update(
            @Valid @RequestBody UpdateShipperRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireShipper(actor);
        // We don't have the shipperId from the request/URL, we only have principalId, 
        // wait, the domain actor contains principalId and it will infer the shipperId for self-updates
        // Wait, UpdateProfile command requires shipperId.
        // I need to fetch shipperId by reading my profile.
        var profile = readSelf.execute(toActor(actor));
        var command = new ShipperCommands.UpdateProfile(
                toActor(actor),
                profile.id(),
                request.getFullName(),
                request.getVehicleType(),
                request.getLicenseNumber(),
                request.getIdCard(),
                request.getPhone(),
                request.getLicensePlate()
        );
        var result = updateProfile.execute(command);
        return ResponseEntity.ok(new BaseResponse<>(1, shipperMapper.toResponse(result)));
    }

    @PatchMapping("/online-status")
    public ResponseEntity<BaseResponse<ShipperResponse>> updateOnlineStatus(
            @RequestParam Boolean isOnline,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireShipper(actor);
        var command = new ShipperCommands.SetOnlineStatus(toActor(actor), isOnline != null && isOnline);
        var result = setOnlineStatus.execute(command);
        return ResponseEntity.ok(new BaseResponse<>(1, shipperMapper.toResponse(result)));
    }

    // Admin endpoints
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<ShipperResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdmin(actor);
        var result = readById.execute(toActor(actor), id);
        return ResponseEntity.ok(new BaseResponse<>(1, shipperMapper.toResponse(result)));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<ShipperResponse>>> getAll(
            Pageable pageable,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdmin(actor);
        var request = new PageRequest(pageable.getPageNumber(), pageable.getPageSize(), null, null);
        var result = readPage.execute(toActor(actor), request);
        var mappedList = result.page().items().stream().map(shipperMapper::toResponse).toList();
        Page<ShipperResponse> page = new PageImpl<>(mappedList, pageable, result.page().totalItems());
        return ResponseEntity.ok(new BaseResponse<>(1, PageResponse.from(page)));
    }

    @GetMapping("/online")
    public ResponseEntity<BaseResponse<List<ShipperResponse>>> getOnlineShippers(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdmin(actor);
        var request = new PageRequest(0, PageRequest.MAX_SIZE, true, null);
        var result = readPage.execute(toActor(actor), request);
        var mappedList = result.page().items().stream().map(shipperMapper::toResponse).toList();
        return ResponseEntity.ok(new BaseResponse<>(1, mappedList));
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
