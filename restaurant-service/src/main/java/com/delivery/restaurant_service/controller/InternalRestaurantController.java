package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.payload.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants/internal")
@RequiredArgsConstructor
public class InternalRestaurantController {

    private final RestaurantRepository restaurantRepository;

    @Value("${app.internal.secret:}")
    private String internalSecret;

    @GetMapping("/{restaurantId}/owners/{ownerId}")
    public ResponseEntity<BaseResponse<Boolean>> isOwnedBy(
            @PathVariable Long restaurantId,
            @PathVariable Long ownerId,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (internalSecret == null || internalSecret.isBlank()
                || !internalSecret.equals(internalToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Forbidden"));
        }
        return ResponseEntity.ok(new BaseResponse<>(
                1,
                restaurantRepository.existsByIdAndCreatorId(restaurantId, ownerId)));
    }
}
