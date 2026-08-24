package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.dto.response.ServiceabilityDecisionResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.RestaurantServiceabilityService;
import com.delivery.restaurant_service.service.ServiceabilityDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Private Order → Restaurant serviceability decision boundary. */
@RestController
@RequestMapping("/api/restaurants/internal")
@RequiredArgsConstructor
public class InternalServiceabilityController {

    private final RestaurantServiceabilityService serviceabilityService;

    @Value("${app.internal.secret:}")
    private String internalSecret;

    @GetMapping("/{restaurantId}/serviceability")
    public ResponseEntity<BaseResponse<ServiceabilityDecisionResponse>> evaluate(
            @PathVariable Long restaurantId,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestHeader(value = "Internal-Token", required = false) String token) {
        if (internalSecret == null || internalSecret.isBlank() || !internalSecret.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Forbidden"));
        }
        ServiceabilityDecision decision = serviceabilityService.evaluate(restaurantId, latitude, longitude);
        ServiceabilityDecisionResponse response = new ServiceabilityDecisionResponse(
                decision.enabled(), decision.serviceable(), decision.zoneId(),
                decision.zoneRevision(), decision.reason());
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }
}
