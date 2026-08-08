package com.delivery.shipper_service.controller;

import com.delivery.shipper_service.dto.response.ShipperRatingResponse;
import com.delivery.shipper_service.payload.BaseResponse;
import com.delivery.shipper_service.service.IShipperRatingService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shippers")
@RequiredArgsConstructor
public class ShipperRatingController {

    private final IShipperRatingService shipperRatingService;

    @GetMapping("/me/ratings")
    public ResponseEntity<BaseResponse<List<ShipperRatingResponse>>> getMyRatings(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (actor == null || !actor.isShipper()) {
            throw new AccessDeniedException("Không có quyền truy cập");
        }
        List<ShipperRatingResponse> responses = shipperRatingService.getMyRatings(actor.getUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }
}
