package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.payload.BaseResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@RestController
@RequestMapping("/api/restaurants/internal")
public class InternalRestaurantController {

    private final RestaurantRepository restaurantRepository;
    private final MeterRegistry meterRegistry;

    @Value("${app.internal.secret:}")
    private String internalSecret;

    @Value("${app.identity.principal-ownership.enforced:false}")
    private boolean principalOwnershipEnforced;

    @Autowired
    public InternalRestaurantController(RestaurantRepository restaurantRepository,
                                        MeterRegistry meterRegistry) {
        this.restaurantRepository = restaurantRepository;
        this.meterRegistry = meterRegistry;
    }

    /** Compatibility constructor retained for focused legacy callers. */
    public InternalRestaurantController(RestaurantRepository restaurantRepository) {
        this(restaurantRepository, new SimpleMeterRegistry());
    }

    /** Legacy overload: absence of legacyOwnerId keeps the original lookup. */
    public ResponseEntity<BaseResponse<Boolean>> isOwnedBy(
            Long restaurantId, Long ownerId, String internalToken) {
        return isOwnedBy(restaurantId, ownerId, null, internalToken);
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
        // A caller without the query parameter is an old internal client and
        // keeps legacy behaviour. Principal-aware callers supply both values;
        // legacy fallback is allowed only for rows not yet backfilled.
        boolean owned;
        if (legacyOwnerId == null) {
            owned = restaurantRepository.existsByIdAndCreatorId(restaurantId, ownerId);
        } else if (principalOwnershipEnforced) {
            owned = restaurantRepository.existsByIdAndOwnerPrincipalId(restaurantId, ownerId);
        } else {
            // Count only a real fallback, never every principal-aware internal
            // request. This makes the R4 zero-fallback gate meaningful for
            // Flash Sale and any other caller using this ownership boundary.
            owned = restaurantRepository.existsByIdAndOwnerPrincipalId(restaurantId, ownerId);
            if (!owned) {
                owned = restaurantRepository.existsByIdAndOwnerPrincipalOrUnmigratedCreator(
                        restaurantId, ownerId, legacyOwnerId);
                if (owned) identityLegacyFallback();
            }
        }
        return ResponseEntity.ok(new BaseResponse<>(1, owned));
    }

    private void identityLegacyFallback() {
        Counter.builder("delivery.identity.legacy.fallback")
                .tag("service", "restaurant").tag("surface", "internal_owner_check")
                .register(meterRegistry).increment();
    }
}
