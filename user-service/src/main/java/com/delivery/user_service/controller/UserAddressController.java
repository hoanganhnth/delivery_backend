package com.delivery.user_service.controller;

import com.delivery.user_service.dto.UserAddressRequest;
import com.delivery.user_service.dto.UserAddressResponse;
import com.delivery.user_service.service.UserAddressService;
import com.delivery.user_service.service.UserService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.delivery.user_service.payload.BaseResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService addressService;
    private final UserService userService;

    @GetMapping("/users/{userId}/addresses")
    public ResponseEntity<BaseResponse<List<UserAddressResponse>>> getUserAddresses(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (!isSelfOrAdmin(userId, actor)) {
            return forbidden();
        }
        List<UserAddressResponse> addresses = addressService.getAllAddressesByUser(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, addresses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<UserAddressResponse>> getAddress(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        UserAddressResponse address = addressService.getAddressById(id);
        if (!isSelfOrAdmin(address.getUserId(), actor)) {
            return forbidden();
        }
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }

    @PostMapping("/users/{userId}/addresses")
    public ResponseEntity<BaseResponse<UserAddressResponse>> createAddress(
            @PathVariable Long userId,
            @Valid @RequestBody UserAddressRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (!isSelfOrAdmin(userId, actor)) {
            return forbidden();
        }
        UserAddressResponse address = addressService.createAddress(userId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<UserAddressResponse>> updateAddress(
            @PathVariable Long id,
            @Valid @RequestBody UserAddressRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        UserAddressResponse existing = addressService.getAddressById(id);
        if (!isSelfOrAdmin(existing.getUserId(), actor)) {
            return forbidden();
        }
        UserAddressResponse address = addressService.updateAddress(id, request);
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteAddress(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        UserAddressResponse existing = addressService.getAddressById(id);
        if (!isSelfOrAdmin(existing.getUserId(), actor)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Bạn không có quyền truy cập địa chỉ này"));
        }
        addressService.deleteAddress(id);
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Xóa địa chỉ thành công"));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<BaseResponse<UserAddressResponse>> setDefault(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        UserAddressResponse existing = addressService.getAddressById(id);
        if (!isSelfOrAdmin(existing.getUserId(), actor)) {
            return forbidden();
        }
        UserAddressResponse address = addressService.setDefaultAddress(id);
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }

    private boolean isSelfOrAdmin(Long ownerId, AuthenticatedActor actor) {
        if (actor == null) {
            return false;
        }
        if (actor.isAdmin()) {
            return true;
        }
        if (!actor.isUser() || actor.getPrincipalId() == null || ownerId == null) {
            return false;
        }
        // Address rows stay keyed by profile ID. Resolve that profile inside the
        // service that owns it instead of trusting the migration-era JWT subject.
        return ownerId.equals(userService.getUserByPrincipalId(actor.getPrincipalId()).getId());
    }

    private <T> ResponseEntity<BaseResponse<T>> forbidden() {
        return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Bạn không có quyền truy cập địa chỉ này"));
    }
}
