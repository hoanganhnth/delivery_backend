package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.dto.request.InventoryReservationRequest;
import com.delivery.restaurant_service.dto.response.InventoryReservationResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.MenuItemInventoryReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Private Order → Restaurant inventory reservation boundary. */
@RestController
@RequestMapping("/api/menu-items/internal/inventory")
@RequiredArgsConstructor
public class InternalInventoryController {

    private final ObjectProvider<MenuItemInventoryReservationService> serviceProvider;

    @Value("${app.internal.secret:}")
    private String internalSecret;

    @Value("${app.restaurant.inventory-enabled:false}")
    private boolean inventoryEnabled;

    @PostMapping("/reservations")
    public ResponseEntity<BaseResponse<InventoryReservationResponse>> reserve(
            @Valid @RequestBody(required = false) InventoryReservationRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String token) {
        ResponseEntity<BaseResponse<InventoryReservationResponse>> denied = requireInternal(token);
        if (denied != null) return denied;
        if (request == null) return bad("Invalid inventory reservation request");
        try {
            return ResponseEntity.ok(new BaseResponse<>(1, service().reserve(request), "Inventory reserved"));
        } catch (IllegalArgumentException invalid) {
            return bad(invalid.getMessage());
        }
    }

    @PostMapping("/reservations/{reservationId}/commit")
    public ResponseEntity<BaseResponse<InventoryReservationResponse>> commit(
            @PathVariable UUID reservationId,
            @RequestParam Long orderId,
            @RequestHeader(value = "Internal-Token", required = false) String token) {
        ResponseEntity<BaseResponse<InventoryReservationResponse>> denied = requireInternal(token);
        if (denied != null) return denied;
        try {
            return ResponseEntity.ok(new BaseResponse<>(1,
                    service().commit(reservationId, orderId), "Inventory committed"));
        } catch (IllegalArgumentException invalid) {
            return bad(invalid.getMessage());
        }
    }

    @PostMapping("/reservations/{reservationId}/release")
    public ResponseEntity<BaseResponse<InventoryReservationResponse>> release(
            @PathVariable UUID reservationId,
            @RequestParam Long orderId,
            @RequestHeader(value = "Internal-Token", required = false) String token) {
        ResponseEntity<BaseResponse<InventoryReservationResponse>> denied = requireInternal(token);
        if (denied != null) return denied;
        try {
            return ResponseEntity.ok(new BaseResponse<>(1,
                    service().release(reservationId, orderId), "Inventory released"));
        } catch (IllegalArgumentException invalid) {
            return bad(invalid.getMessage());
        }
    }

    private MenuItemInventoryReservationService service() {
        MenuItemInventoryReservationService service = serviceProvider.getIfAvailable();
        if (service == null) throw new IllegalStateException("Inventory reservation is unavailable");
        return service;
    }

    private ResponseEntity<BaseResponse<InventoryReservationResponse>> requireInternal(String token) {
        if (internalSecret == null || internalSecret.isBlank() || !internalSecret.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Forbidden"));
        }
        if (!inventoryEnabled || serviceProvider.getIfAvailable() == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new BaseResponse<>(0, null, "Inventory reservation is disabled"));
        }
        return null;
    }

    private ResponseEntity<BaseResponse<InventoryReservationResponse>> bad(String message) {
        return ResponseEntity.badRequest().body(new BaseResponse<>(0, null,
                message == null || message.isBlank() ? "Invalid inventory request" : message));
    }
}
