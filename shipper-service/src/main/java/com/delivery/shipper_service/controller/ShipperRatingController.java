package com.delivery.shipper_service.controller;

import com.delivery.shipper_service.dto.response.ShipperRatingResponse;
import com.delivery.shipper_service.payload.BaseResponse;
import com.delivery.shipper_service.service.IShipperRatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shippers")
@RequiredArgsConstructor
public class ShipperRatingController {

    private final IShipperRatingService shipperRatingService;

    @GetMapping("/me/ratings")
    public ResponseEntity<BaseResponse<List<ShipperRatingResponse>>> getMyRatings(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Role") String role) {
        if (!"SHIPPER".equals(role)) {
            throw new AccessDeniedException("Không có quyền truy cập");
        }
        List<ShipperRatingResponse> responses = shipperRatingService.getMyRatings(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, responses));
    }
}
