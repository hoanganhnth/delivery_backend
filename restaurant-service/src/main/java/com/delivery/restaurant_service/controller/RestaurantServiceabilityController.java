package com.delivery.restaurant_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.restaurant.application.api.CreateServiceabilityZoneCommand;
import com.delivery.restaurant.application.api.RestaurantServiceabilityUseCase;
import com.delivery.restaurant.application.api.ServiceabilityZoneResult;
import com.delivery.restaurant.application.api.UpdateServiceabilityZoneCommand;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant_service.dto.request.CreateServiceabilityZoneRequest;
import com.delivery.restaurant_service.dto.request.UpdateServiceabilityZoneRequest;
import com.delivery.restaurant_service.dto.response.ServiceabilityZoneResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/serviceability-zones")
@RequiredArgsConstructor
public class RestaurantServiceabilityController {

    private final RestaurantServiceabilityUseCase serviceabilityService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<ServiceabilityZoneResponse>>> list(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        return ResponseEntity.ok(new BaseResponse<>(1, serviceabilityService.list(
                restaurantId, actor.getPrincipalId(), actor.getLegacyUserId(), actorRole(actor)).stream()
                .map(RestaurantServiceabilityController::toResponse).toList()));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<ServiceabilityZoneResponse>> create(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateServiceabilityZoneRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        return ResponseEntity.ok(new BaseResponse<>(1, toResponse(serviceabilityService.create(
                restaurantId, new CreateServiceabilityZoneCommand(request.getName(), request.getPolygonGeoJson(),
                        request.getPriority(), request.getActive()), actor.getPrincipalId(), actor.getLegacyUserId(),
                actorRole(actor)))));
    }

    @PutMapping("/{zoneId}")
    public ResponseEntity<BaseResponse<ServiceabilityZoneResponse>> update(
            @PathVariable Long restaurantId,
            @PathVariable Long zoneId,
            @Valid @RequestBody UpdateServiceabilityZoneRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        return ResponseEntity.ok(new BaseResponse<>(1, toResponse(serviceabilityService.update(
                restaurantId, zoneId, new UpdateServiceabilityZoneCommand(request.getRevision(), request.getName(),
                        request.getPolygonGeoJson(), request.getPriority(), request.getActive()),
                actor.getPrincipalId(), actor.getLegacyUserId(), actorRole(actor)))));
    }

    @DeleteMapping("/{zoneId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable Long restaurantId,
            @PathVariable Long zoneId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        serviceabilityService.delete(restaurantId, zoneId,
                actor.getPrincipalId(), actor.getLegacyUserId(), actorRole(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, null));
    }

    private void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.getPrincipalId() == null || actor.getLegacyUserId() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập");
        }
    }

    private RestaurantActorRole actorRole(AuthenticatedActor actor) {
        if (actor.isAdmin()) return RestaurantActorRole.ADMIN;
        if (actor.isShopOwner()) return RestaurantActorRole.SHOP_OWNER;
        return RestaurantActorRole.OTHER;
    }

    private static ServiceabilityZoneResponse toResponse(ServiceabilityZoneResult result) {
        ServiceabilityZoneResponse response = new ServiceabilityZoneResponse();
        response.setId(result.id());
        response.setRestaurantId(result.restaurantId());
        response.setName(result.name());
        response.setPolygonGeoJson(result.polygonGeoJson());
        response.setPriority(result.priority());
        response.setActive(result.active());
        response.setRevision(result.revision());
        response.setCreatedAt(result.createdAt());
        response.setUpdatedAt(result.updatedAt());
        return response;
    }
}
