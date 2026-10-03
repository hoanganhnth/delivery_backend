package com.delivery.restaurant_service.controller;

import com.delivery.restaurant.application.api.RestaurantOwnershipLookupUseCase;
import com.delivery.restaurant_service.payload.BaseResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants/internal")
public class InternalRestaurantController {

    private final RestaurantOwnershipLookupUseCase ownership;
    private final MeterRegistry meterRegistry;

    @Value("${app.internal.secret:}")
    private String internalSecret;

    @Value("${app.identity.principal-ownership.enforced:false}")
    private boolean principalOwnershipEnforced;

    public InternalRestaurantController(RestaurantOwnershipLookupUseCase ownership,
                                        MeterRegistry meterRegistry) {
        this.ownership = ownership;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/{restaurantId}/owners/{ownerId}")
    public ResponseEntity<BaseResponse<Boolean>> isOwnedBy(
            @PathVariable Long restaurantId,
            @PathVariable Long ownerId,
            @RequestParam(value = "legacyOwnerId", required = false) Long legacyOwnerId,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (internalSecret == null || internalSecret.isBlank()
                || !internalSecret.equals(internalToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Forbidden"));
        }
        var decision = ownership.internalCheck(restaurantId, ownerId, legacyOwnerId, principalOwnershipEnforced);
        if (decision.usedLegacyFallback()) identityLegacyFallback();
        return ResponseEntity.ok(new BaseResponse<>(1, decision.owned()));
    }

    private void identityLegacyFallback() {
        Counter.builder("delivery.identity.legacy.fallback")
                .tag("service", "restaurant").tag("surface", "internal_owner_check")
                .register(meterRegistry).increment();
    }
}
