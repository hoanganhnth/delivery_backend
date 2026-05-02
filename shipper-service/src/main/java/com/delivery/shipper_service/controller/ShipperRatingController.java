package com.delivery.shipper_service.controller;

import com.delivery.shipper_service.dto.request.ShipperRatingRequest;
import com.delivery.shipper_service.dto.response.ShipperRatingResponse;
import com.delivery.shipper_service.service.IShipperRatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shippers")
@RequiredArgsConstructor
public class ShipperRatingController {

    private final IShipperRatingService shipperRatingService;

    @PostMapping("/{shipperId}/ratings")
    public ResponseEntity<ShipperRatingResponse> submitRating(
            @PathVariable Long shipperId,
            @RequestHeader("X-User-Id") Long customerId,
            @RequestBody ShipperRatingRequest request) {
        
        ShipperRatingResponse response = shipperRatingService.submitRating(shipperId, customerId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{shipperId}/ratings")
    public ResponseEntity<List<ShipperRatingResponse>> getShipperRatings(@PathVariable Long shipperId) {
        List<ShipperRatingResponse> responses = shipperRatingService.getShipperRatings(shipperId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/me/ratings")
    public ResponseEntity<List<ShipperRatingResponse>> getMyRatings(@RequestHeader("X-User-Id") Long userId) {
        List<ShipperRatingResponse> responses = shipperRatingService.getMyRatings(userId);
        return ResponseEntity.ok(responses);
    }
}
