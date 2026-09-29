package com.delivery.shipper_service.controller;

import com.delivery.shipper_service.dto.response.ShipperRatingResponse;
import com.delivery.shipper_service.payload.BaseResponse;
import com.delivery.shipper.application.api.ShipperUseCases;
import com.delivery.shipper.application.api.ShipperCommands;
import com.delivery.shipper.domain.identity.ShipperRole;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shippers")
public class ShipperRatingController {

    private final ShipperUseCases.ReadSelfRatings readSelfRatings;

    public ShipperRatingController(ShipperUseCases.ReadSelfRatings readSelfRatings) {
        this.readSelfRatings = readSelfRatings;
    }

    private ShipperCommands.Actor toActor(AuthenticatedActor actor) {
        ShipperRole role = actor.isAdmin() ? ShipperRole.ADMIN : ShipperRole.SHIPPER;
        return new ShipperCommands.Actor(actor.getPrincipalId(), actor.getLegacyUserId(), role);
    }

    @GetMapping("/me/ratings")
    public ResponseEntity<BaseResponse<List<ShipperRatingResponse>>> getMyRatings(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (actor == null || !actor.isShipper()) {
            throw new AccessDeniedException("Không có quyền truy cập");
        }
        var items = readSelfRatings.execute(toActor(actor));
        var responses = items.stream().map(item -> {
            var response = new ShipperRatingResponse();
            response.setShipperId(item.shipperId());
            response.setCustomerId(item.customerId());
            response.setOrderId(item.orderId());
            response.setRating(item.score());
            response.setComment(item.comment());
            return response;
        }).toList();
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }
}
